"""Main entry point for the Bot Player service.

Manages the full bot lifecycle:
- Load configuration from environment variables
- Set up structured JSON logging (never logs the session token)
- Start /health HTTP endpoint on configured port
- Connect WebSocket, subscribe to player topics
- Event loop: react to game events via the strategy module
- Track money balance, enforce financial constraints
- Handle GAME_ENDED and SIGTERM/SIGINT for graceful shutdown
"""
import json
import logging
import signal
import sys
import threading
import time
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from flask import Flask, jsonify

from client import BotClient, AuthFailedError
from config import BotConfig, get_config
from models import Action, BasestationState, GameState
from strategy import rank_actions


# ---------------------------------------------------------------------------
# Structured JSON Logging
# ---------------------------------------------------------------------------


class _TokenFilter(logging.Filter):
    """Filter that redacts the session token from log output."""

    def __init__(self, token: str):
        super().__init__()
        self._token = token

    def filter(self, record: logging.LogRecord) -> bool:
        """Redact token from the message and args."""
        if self._token and hasattr(record, "msg"):
            if isinstance(record.msg, str) and self._token in record.msg:
                record.msg = record.msg.replace(self._token, "[REDACTED]")
            if record.args:
                new_args = []
                for arg in (record.args if isinstance(record.args, tuple) else (record.args,)):
                    if isinstance(arg, str) and self._token in arg:
                        new_args.append(arg.replace(self._token, "[REDACTED]"))
                    else:
                        new_args.append(arg)
                record.args = tuple(new_args)
        return True


class _JsonFormatter(logging.Formatter):
    """JSON log formatter that includes session context fields."""

    def __init__(self, session_code: str, bot_display_name: str, difficulty: str):
        super().__init__()
        self._session_code = session_code
        self._bot_display_name = bot_display_name
        self._difficulty = difficulty

    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "session_code": self._session_code,
            "bot_display_name": self._bot_display_name,
            "difficulty": self._difficulty,
        }
        if record.exc_info and record.exc_info[0] is not None:
            entry["exception"] = self.formatException(record.exc_info)
        return json.dumps(entry)


# ---------------------------------------------------------------------------
# Health Endpoint
# ---------------------------------------------------------------------------


def _create_health_app() -> Flask:
    """Create a minimal Flask app with a /health endpoint."""
    app = Flask(__name__)

    # Suppress Flask's default request logging
    flask_log = logging.getLogger("werkzeug")
    flask_log.setLevel(logging.WARNING)

    @app.route("/health")
    def health():
        return jsonify({"status": "ok"}), 200

    return app


def _start_health_server(port: int) -> threading.Thread:
    """Start the Flask health endpoint in a daemon background thread."""
    app = _create_health_app()
    thread = threading.Thread(
        target=lambda: app.run(host="0.0.0.0", port=port, threaded=True),
        daemon=True,
        name="health-server",
    )
    thread.start()
    return thread


# ---------------------------------------------------------------------------
# Bot Player State
# ---------------------------------------------------------------------------


class BotPlayer:
    """Encapsulates the bot's runtime state and event handling logic."""

    def __init__(self, config: BotConfig, client: BotClient):
        self._config = config
        self._client = client
        self._logger = logging.getLogger(__name__)

        # Game state tracking
        self._game_state = GameState(
            basestations=[],
            money=1000.0,
            catalogue=[],
            difficulty=config.difficulty.value,
        )

        # Lifecycle flags
        self._shutdown_requested = False
        self._game_ended = False
        self._pending_action = False

        # Metrics
        self._actions_taken = 0
        self._player_id: Optional[int] = None
        self._bot_display_name: str = f"Bot-{config.session_code[:6]}"

    @property
    def shutdown_requested(self) -> bool:
        return self._shutdown_requested

    @property
    def game_ended(self) -> bool:
        return self._game_ended

    @property
    def bot_display_name(self) -> str:
        return self._bot_display_name

    def request_shutdown(self) -> None:
        """Signal the bot to shut down gracefully."""
        self._shutdown_requested = True

    # -------------------------------------------------------------------------
    # Initialisation
    # -------------------------------------------------------------------------

    def initialise(self) -> bool:
        """Fetch initial game state (basestations, catalogue).

        Returns:
            True if initialisation succeeded, False otherwise.
        """
        try:
            # Fetch catalogue
            catalogue = self._client.get_catalogue()
            self._game_state.catalogue = catalogue
            self._logger.info(
                "Loaded catalogue with %d rApp templates", len(catalogue)
            )

            # Fetch basestations
            basestations_raw = self._client.get_basestations()
            self._game_state.basestations = [
                BasestationState(
                    id=bs.get("id", 0),
                    name=bs.get("name", "Unknown"),
                    metrics=bs.get("metrics", {}),
                    deployed_rapps=bs.get("deployedRapps", bs.get("deployed_rapps", [])),
                    active_events=bs.get("activeEvents", bs.get("active_events", [])),
                )
                for bs in basestations_raw
            ]
            self._logger.info(
                "Loaded %d basestations", len(self._game_state.basestations)
            )

            # Try to extract player_id from basestations response context
            # The player_id is needed for WebSocket subscriptions
            # It's typically derived from the session join response;
            # for now we'll use 0 and let the subscribe work with topics
            return True

        except AuthFailedError:
            self._logger.error("Auth failed during initialisation — cannot continue")
            return False
        except Exception as e:
            self._logger.error("Failed to initialise game state: %s", e)
            return False

    def set_player_id(self, player_id: int) -> None:
        """Set the player ID for WebSocket subscriptions."""
        self._player_id = player_id

    # -------------------------------------------------------------------------
    # WebSocket Event Handlers
    # -------------------------------------------------------------------------

    def handle_events(self, message: Dict[str, Any]) -> None:
        """Handle EVENT_OCCURRED messages from WebSocket.

        Invokes the strategy module, applies response delay, and executes
        the top-ranked action.
        """
        msg_type = message.get("type") or message.get("messageType", "")

        if msg_type == "EVENT_OCCURRED":
            self._logger.info(
                "Event received: %s (severity: %s)",
                message.get("eventType", "UNKNOWN"),
                message.get("severity", "UNKNOWN"),
            )
            # Update basestation events in game state
            self._update_basestation_events(message)
            # Invoke strategy and execute
            self._invoke_strategy_and_act()

    def handle_metrics(self, message: Dict[str, Any]) -> None:
        """Handle METRICS_UPDATED messages — update internal game state."""
        msg_type = message.get("type") or message.get("messageType", "")

        if msg_type == "METRICS_UPDATED":
            basestation_id = message.get("basestationId") or message.get("basestation_id")
            metrics = message.get("metrics", {})

            if basestation_id is not None:
                for bs in self._game_state.basestations:
                    if bs.id == basestation_id:
                        bs.metrics = metrics
                        self._logger.debug(
                            "Updated metrics for basestation %d", basestation_id
                        )
                        break

    def handle_rapps(self, message: Dict[str, Any]) -> None:
        """Handle RAPP_STATUS_CHANGED messages — update deployed rApp tracking."""
        msg_type = message.get("type") or message.get("messageType", "")

        if msg_type == "RAPP_STATUS_CHANGED":
            basestation_id = message.get("basestationId") or message.get("basestation_id")
            rapp_data = message.get("rapp") or message.get("deployment", {})
            rapp_id = rapp_data.get("id")
            new_status = rapp_data.get("status") or message.get("status")

            if basestation_id is not None and rapp_id is not None:
                for bs in self._game_state.basestations:
                    if bs.id == basestation_id:
                        # Update or add the rApp in deployed list
                        updated = False
                        for rapp in bs.deployed_rapps:
                            if rapp.get("id") == rapp_id:
                                rapp["status"] = new_status
                                updated = True
                                break
                        if not updated and rapp_data:
                            bs.deployed_rapps.append(rapp_data)
                        self._logger.debug(
                            "Updated rApp %d status to %s on basestation %d",
                            rapp_id,
                            new_status,
                            basestation_id,
                        )
                        break

    def handle_game(self, message: Dict[str, Any]) -> None:
        """Handle game-wide messages including GAME_ENDED."""
        msg_type = message.get("type") or message.get("messageType", "")

        if msg_type == "GAME_ENDED":
            self._logger.info("GAME_ENDED received — initiating shutdown")
            self._game_ended = True
            # Allow pending action to complete, then shut down
            self._shutdown_requested = True

    def handle_leaderboard(self, message: Dict[str, Any]) -> None:
        """Handle LEADERBOARD_UPDATED messages — refresh money balance."""
        msg_type = message.get("type") or message.get("messageType", "")

        if msg_type == "LEADERBOARD_UPDATED":
            entries = message.get("leaderboard") or message.get("entries", [])
            # Find our bot's entry and extract the money balance
            for entry in entries:
                player_name = entry.get("displayName") or entry.get("playerName", "")
                player_id = entry.get("playerId") or entry.get("player_id")

                # Match by player_id or display name
                if (
                    (self._player_id is not None and player_id == self._player_id)
                    or player_name == self._bot_display_name
                ):
                    new_money = entry.get("money")
                    if new_money is not None:
                        old_money = self._game_state.money
                        self._game_state.money = float(new_money)
                        if abs(old_money - self._game_state.money) > 0.01:
                            self._logger.info(
                                "Money balance refreshed from leaderboard: €%.2f → €%.2f",
                                old_money,
                                self._game_state.money,
                            )
                    break

    # -------------------------------------------------------------------------
    # Strategy Invocation and Action Execution
    # -------------------------------------------------------------------------

    def _invoke_strategy_and_act(self) -> None:
        """Invoke strategy module, apply response delay, execute top action."""
        if self._shutdown_requested or self._client.auth_failed:
            return

        self._logger.debug("Invoking strategy module")
        recommendations = rank_actions(self._game_state)

        if not recommendations:
            self._logger.debug("No recommendations from strategy module")
            return

        # Select top recommendation
        top = recommendations[0]
        self._logger.info(
            "Strategy recommends: %s (confidence: %.2f) — %s",
            top.action.value,
            top.confidence,
            top.reasoning,
        )

        # Check financial constraints for DEPLOY actions
        if top.action == Action.DEPLOY and top.rapp_template_id is not None:
            cost = self._get_deploy_cost(top.rapp_template_id)
            if self._game_state.money - cost < 0:
                self._logger.warning(
                    "Skipping DEPLOY — would result in negative balance "
                    "(balance: €%.2f, cost: €%.2f)",
                    self._game_state.money,
                    cost,
                )
                return

        # Apply response delay based on difficulty
        delay = self._config.response_delay
        if delay > 0:
            self._logger.debug("Applying response delay: %.1fs", delay)
            self._pending_action = True
            # Sleep in small intervals to allow shutdown interruption
            elapsed = 0.0
            while elapsed < delay and not self._shutdown_requested:
                sleep_chunk = min(0.5, delay - elapsed)
                time.sleep(sleep_chunk)
                elapsed += sleep_chunk

            if self._shutdown_requested and not self._game_ended:
                self._pending_action = False
                return

        # Execute the action
        self._execute_action(top)
        self._pending_action = False

    def _execute_action(self, rec) -> None:
        """Execute a single Recommendation via the appropriate REST call."""
        if self._client.auth_failed:
            return

        try:
            if rec.action == Action.DEPLOY:
                result = self._client.deploy_rapp(rec.rapp_template_id, rec.basestation_id)
                if result is not None:
                    cost = self._get_deploy_cost(rec.rapp_template_id)
                    self._game_state.money -= cost
                    self._actions_taken += 1
                    self._logger.info(
                        "Deployed rApp template %d to basestation %d (cost: €%.2f, balance: €%.2f)",
                        rec.rapp_template_id,
                        rec.basestation_id,
                        cost,
                        self._game_state.money,
                    )

            elif rec.action == Action.TUNE:
                result = self._client.tune_rapp(rec.deployment_id, {"aggressiveness": 0.7})
                if result is not None:
                    self._actions_taken += 1
                    self._logger.info(
                        "Tuned deployment %d on basestation %d",
                        rec.deployment_id,
                        rec.basestation_id,
                    )

            elif rec.action == Action.DISABLE:
                result = self._client.disable_rapp(rec.deployment_id)
                if result is not None:
                    self._actions_taken += 1
                    self._logger.info(
                        "Disabled deployment %d on basestation %d",
                        rec.deployment_id,
                        rec.basestation_id,
                    )

            elif rec.action == Action.ROLLBACK:
                result = self._client.rollback_rapp(rec.deployment_id)
                if result is not None:
                    self._actions_taken += 1
                    self._logger.info(
                        "Rolled back deployment %d on basestation %d",
                        rec.deployment_id,
                        rec.basestation_id,
                    )

        except AuthFailedError:
            self._logger.error("Auth failed during action execution — ceasing play")
        except Exception as e:
            self._logger.error("Error executing action %s: %s", rec.action.value, e)

    # -------------------------------------------------------------------------
    # Helpers
    # -------------------------------------------------------------------------

    def _get_deploy_cost(self, template_id: int) -> float:
        """Look up deployment cost from the catalogue."""
        for entry in self._game_state.catalogue:
            if entry.get("id") == template_id:
                return float(entry.get("cost", 0))
        return 0.0

    def _update_basestation_events(self, message: Dict[str, Any]) -> None:
        """Update basestation active events from an EVENT_OCCURRED message."""
        basestation_id = message.get("basestationId") or message.get("basestation_id")
        if basestation_id is None:
            return

        event_data = {
            "id": message.get("eventId") or message.get("id"),
            "eventType": message.get("eventType") or message.get("event_type"),
            "severity": message.get("severity"),
            "escalationLevel": message.get("escalationLevel", 0),
        }

        for bs in self._game_state.basestations:
            if bs.id == basestation_id:
                # Add the event if not already tracked
                existing_ids = {e.get("id") for e in bs.active_events if e.get("id")}
                if event_data.get("id") not in existing_ids:
                    bs.active_events.append(event_data)
                break

    def log_shutdown_summary(self) -> None:
        """Log a summary of the bot's activity before shutdown."""
        self._logger.info(
            "Bot shutdown summary — session_code: %s, actions_taken: %d, "
            "final_balance: €%.2f",
            self._config.session_code,
            self._actions_taken,
            self._game_state.money,
        )


# ---------------------------------------------------------------------------
# Main Entry Point
# ---------------------------------------------------------------------------


def main() -> None:
    """Main entry point for the bot player service."""

    # 1. Load configuration (fail fast if invalid)
    try:
        config = BotConfig.from_env()
    except ValueError as e:
        print(f"Configuration error: {e}", file=sys.stderr)
        sys.exit(1)

    # 2. Set up structured JSON logging
    bot_display_name = f"Bot-{config.session_code[:6]}"
    formatter = _JsonFormatter(
        session_code=config.session_code,
        bot_display_name=bot_display_name,
        difficulty=config.difficulty.value,
    )
    token_filter = _TokenFilter(config.session_token)

    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, config.log_level, logging.INFO))
    root_logger.handlers.clear()

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)
    handler.addFilter(token_filter)
    root_logger.addHandler(handler)

    logger = logging.getLogger(__name__)
    logger.info("Bot Player starting")
    logger.info(
        "Config — session_code: %s, difficulty: %s, backend: %s, health_port: %d",
        config.session_code,
        config.difficulty.value,
        config.backend_base_url,
        config.health_port,
    )

    # 3. Start health endpoint
    _start_health_server(config.health_port)
    logger.info("Health endpoint started on port %d", config.health_port)

    # 4. Create client and bot player
    client = BotClient(config)
    bot = BotPlayer(config, client)

    # 5. Register signal handlers for graceful shutdown
    def _signal_handler(signum, frame):
        sig_name = signal.Signals(signum).name
        logger.info("Received %s — initiating graceful shutdown", sig_name)
        bot.request_shutdown()

    signal.signal(signal.SIGTERM, _signal_handler)
    signal.signal(signal.SIGINT, _signal_handler)

    # 6. Initialise game state (fetch basestations and catalogue)
    if not bot.initialise():
        logger.error("Failed to initialise — exiting")
        sys.exit(1)

    # 7. Register WebSocket message handlers
    client.set_message_handler("events", bot.handle_events)
    client.set_message_handler("metrics", bot.handle_metrics)
    client.set_message_handler("rapps", bot.handle_rapps)
    client.set_message_handler("game", bot.handle_game)
    client.set_message_handler("leaderboard", bot.handle_leaderboard)

    # 8. Connect WebSocket and subscribe
    try:
        client.connect_websocket()
        # Use player_id 0 as default — the backend routes by token
        # In production, player_id is derived from the join response
        player_id = 0
        bot.set_player_id(player_id)
        client.subscribe(player_id)
        logger.info("WebSocket connected and subscribed to game topics")
    except Exception as e:
        logger.error("Failed to connect WebSocket: %s", e)
        # Continue running — reconnect logic in client will handle recovery

    # 9. Event loop — keep running until shutdown
    logger.info("Bot Player ready — entering event loop")
    try:
        while not bot.shutdown_requested:
            time.sleep(0.1)
    except KeyboardInterrupt:
        logger.info("KeyboardInterrupt — shutting down")

    # 10. Graceful shutdown
    logger.info("Shutdown initiated — completing pending actions")

    # Wait for any pending action to complete (max 5 seconds)
    shutdown_deadline = time.time() + 5.0
    while bot._pending_action and time.time() < shutdown_deadline:
        time.sleep(0.1)

    # Disconnect WebSocket
    client.disconnect()

    # Log summary
    bot.log_shutdown_summary()

    logger.info("Bot Player stopped")
    sys.exit(0)


if __name__ == "__main__":
    main()
