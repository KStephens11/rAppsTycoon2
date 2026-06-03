"""REST and WebSocket client for Bot Player.

Communicates with the Backend using X-Session-Token authentication.
Follows the same patterns as event-generator/client.py but uses player
auth instead of internal API key.

IMPORTANT: This client NEVER uses X-Internal-Key. That header is reserved
for the event-generator service only.
"""
import logging
import threading
import time
from typing import Any, Callable, Dict, List, Optional

import requests
import stomp

from config import BotConfig


logger = logging.getLogger(__name__)


class BotClientError(Exception):
    """Exception raised when bot API calls fail."""

    pass


class AuthFailedError(BotClientError):
    """Exception raised when authentication fails (401/403)."""

    pass


class BotClient:
    """HTTP and WebSocket client for the bot player.

    Uses X-Session-Token header for all requests. On 401/403, sets
    _auth_failed flag and ceases all further API calls.
    """

    # Exponential backoff settings for WebSocket reconnect
    _RECONNECT_BASE_DELAY = 2.0  # seconds
    _RECONNECT_MAX_ATTEMPTS = 5

    def __init__(self, config: BotConfig, timeout: float = 5.0):
        """Initialize the bot client.

        Args:
            config: BotConfig with session_token, session_code, backend_base_url.
            timeout: Request timeout in seconds (default: 5.0).
        """
        self._config = config
        self._timeout = timeout
        self._base_url = config.backend_base_url.rstrip("/")
        self._session_code = config.session_code
        self._headers = {
            "X-Session-Token": config.session_token,
            "Content-Type": "application/json",
        }
        self._auth_failed = False
        self._ws_connection: Optional[stomp.Connection] = None
        self._ws_listener: Optional["_StompListener"] = None
        self._player_id: Optional[int] = None
        self._message_handlers: Dict[str, Callable] = {}
        self._reconnect_thread: Optional[threading.Thread] = None
        self._shutting_down = False

    @property
    def auth_failed(self) -> bool:
        """Whether authentication has failed (401/403 received)."""
        return self._auth_failed

    def set_message_handler(self, topic_suffix: str, handler: Callable) -> None:
        """Register a handler for messages on a specific topic suffix.

        Args:
            topic_suffix: The topic suffix to match (e.g. 'events', 'metrics').
            handler: Callable that receives the parsed message body dict.
        """
        self._message_handlers[topic_suffix] = handler

    # -------------------------------------------------------------------------
    # REST Methods
    # -------------------------------------------------------------------------

    def get_basestations(self) -> List[Dict[str, Any]]:
        """GET /api/sessions/{code}/basestations.

        Returns:
            List of basestation dicts from the response.

        Raises:
            AuthFailedError: On 401/403.
            BotClientError: On other errors (logged, not fatal).
        """
        url = f"{self._base_url}/api/sessions/{self._session_code}/basestations"
        data = self._get(url)
        if data is None:
            return []
        return data.get("basestations", [])

    def deploy_rapp(self, template_id: int, basestation_id: int) -> Optional[Dict[str, Any]]:
        """POST /api/sessions/{code}/rapps/deploy.

        Args:
            template_id: The rApp catalogue template ID to deploy.
            basestation_id: The target basestation ID.

        Returns:
            Deployment response dict, or None on non-auth error.

        Raises:
            AuthFailedError: On 401/403.
        """
        url = f"{self._base_url}/api/sessions/{self._session_code}/rapps/deploy"
        body = {"templateId": template_id, "basestationId": basestation_id}
        return self._post(url, body)

    def tune_rapp(self, deployment_id: int, configuration: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """PUT /api/sessions/{code}/rapps/{id}/tune.

        Args:
            deployment_id: The deployed rApp ID to tune.
            configuration: Dict with threshold and aggressiveness.

        Returns:
            Tune response dict, or None on non-auth error.

        Raises:
            AuthFailedError: On 401/403.
        """
        url = f"{self._base_url}/api/sessions/{self._session_code}/rapps/{deployment_id}/tune"
        body = {"configuration": configuration}
        return self._put(url, body)

    def disable_rapp(self, deployment_id: int) -> Optional[Dict[str, Any]]:
        """PUT /api/sessions/{code}/rapps/{id}/disable.

        Args:
            deployment_id: The deployed rApp ID to disable.

        Returns:
            Disable response dict, or None on non-auth error.

        Raises:
            AuthFailedError: On 401/403.
        """
        url = f"{self._base_url}/api/sessions/{self._session_code}/rapps/{deployment_id}/disable"
        return self._put(url)

    def rollback_rapp(self, deployment_id: int) -> Optional[Dict[str, Any]]:
        """PUT /api/sessions/{code}/rapps/{id}/rollback.

        Args:
            deployment_id: The deployed rApp ID to rollback.

        Returns:
            Rollback response dict, or None on non-auth error.

        Raises:
            AuthFailedError: On 401/403.
        """
        url = f"{self._base_url}/api/sessions/{self._session_code}/rapps/{deployment_id}/rollback"
        return self._put(url)

    def get_catalogue(self) -> List[Dict[str, Any]]:
        """GET /api/rapps/catalogue.

        Returns:
            List of rApp catalogue entries.

        Raises:
            AuthFailedError: On 401/403.
            BotClientError: On other errors (logged, not fatal).
        """
        url = f"{self._base_url}/api/rapps/catalogue"
        data = self._get(url)
        if data is None:
            return []
        return data.get("rapps", [])

    # -------------------------------------------------------------------------
    # WebSocket / STOMP Methods
    # -------------------------------------------------------------------------

    def connect_websocket(self) -> None:
        """Connect to the backend via STOMP over WebSocket.

        Uses X-Session-Token in the STOMP CONNECT frame headers.
        """
        if self._auth_failed:
            logger.warning("Auth failed — skipping WebSocket connect")
            return

        host = self._parse_ws_host()
        port = self._parse_ws_port()

        self._ws_connection = stomp.Connection(
            [(host, port)],
            ws=True,
            ws_path="/ws/game",
        )

        self._ws_listener = _StompListener(self)
        self._ws_connection.set_listener("bot-listener", self._ws_listener)

        connect_headers = {"X-Session-Token": self._config.session_token}

        logger.info("Connecting STOMP WebSocket to %s:%d/ws/game", host, port)
        self._ws_connection.connect(headers=connect_headers, wait=True)
        logger.info("STOMP WebSocket connected")

    def subscribe(self, player_id: int) -> None:
        """Subscribe to player-specific and game-wide topics.

        Subscribes to:
        - /topic/session/{code}/player/{playerId}/events
        - /topic/session/{code}/player/{playerId}/metrics
        - /topic/session/{code}/player/{playerId}/rapps
        - /topic/session/{code}/game
        - /topic/session/{code}/leaderboard

        Args:
            player_id: The bot's player ID for topic subscriptions.
        """
        if self._auth_failed:
            logger.warning("Auth failed — skipping WebSocket subscribe")
            return

        if self._ws_connection is None or not self._ws_connection.is_connected():
            logger.error("Cannot subscribe — WebSocket not connected")
            return

        self._player_id = player_id
        code = self._session_code

        topics = [
            f"/topic/session/{code}/player/{player_id}/events",
            f"/topic/session/{code}/player/{player_id}/metrics",
            f"/topic/session/{code}/player/{player_id}/rapps",
            f"/topic/session/{code}/game",
            f"/topic/session/{code}/leaderboard",
        ]

        for i, topic in enumerate(topics):
            sub_id = f"sub-{i}"
            self._ws_connection.subscribe(destination=topic, id=sub_id, ack="auto")
            logger.debug("Subscribed to %s (id=%s)", topic, sub_id)

        logger.info("Subscribed to %d topics for player %d", len(topics), player_id)

    def disconnect(self) -> None:
        """Gracefully disconnect the WebSocket connection."""
        self._shutting_down = True

        if self._ws_connection is not None:
            try:
                if self._ws_connection.is_connected():
                    self._ws_connection.disconnect()
                    logger.info("STOMP WebSocket disconnected gracefully")
            except Exception as e:
                logger.warning("Error during WebSocket disconnect: %s", e)
            finally:
                self._ws_connection = None

    # -------------------------------------------------------------------------
    # Internal HTTP Helpers
    # -------------------------------------------------------------------------

    def _check_auth_failed(self) -> None:
        """Check if auth has failed and raise if so."""
        if self._auth_failed:
            raise AuthFailedError(
                "Authentication failed — all further API calls are suppressed"
            )

    def _handle_response(self, response: requests.Response, url: str) -> Optional[Dict[str, Any]]:
        """Handle HTTP response, checking for auth failures.

        Args:
            response: The HTTP response object.
            url: The request URL (for logging).

        Returns:
            Parsed JSON dict on success, None on non-auth errors.

        Raises:
            AuthFailedError: On 401/403.
        """
        status = response.status_code

        if status in (401, 403):
            self._auth_failed = True
            logger.error(
                "Auth failed (%d) on %s — ceasing all further API calls",
                status,
                url,
            )
            raise AuthFailedError(f"HTTP {status} on {url}")

        if 200 <= status < 300:
            return response.json()

        # Other errors: log and continue
        logger.warning(
            "Request to %s returned %d: %s",
            url,
            status,
            response.text[:200],
        )
        return None

    def _get(self, url: str) -> Optional[Dict[str, Any]]:
        """Execute a GET request with X-Session-Token.

        Args:
            url: The full URL to GET.

        Returns:
            Parsed JSON response on success, None on non-auth errors.

        Raises:
            AuthFailedError: On 401/403.
        """
        self._check_auth_failed()

        try:
            response = requests.get(
                url,
                headers=self._headers,
                timeout=self._timeout,
            )
            return self._handle_response(response, url)
        except AuthFailedError:
            raise
        except requests.exceptions.RequestException as e:
            logger.warning("Network error on GET %s: %s", url, e)
            return None

    def _post(self, url: str, body: Optional[Dict[str, Any]] = None) -> Optional[Dict[str, Any]]:
        """Execute a POST request with X-Session-Token.

        Args:
            url: The full URL to POST.
            body: JSON request body (optional).

        Returns:
            Parsed JSON response on success, None on non-auth errors.

        Raises:
            AuthFailedError: On 401/403.
        """
        self._check_auth_failed()

        try:
            response = requests.post(
                url,
                json=body,
                headers=self._headers,
                timeout=self._timeout,
            )
            return self._handle_response(response, url)
        except AuthFailedError:
            raise
        except requests.exceptions.RequestException as e:
            logger.warning("Network error on POST %s: %s", url, e)
            return None

    def _put(self, url: str, body: Optional[Dict[str, Any]] = None) -> Optional[Dict[str, Any]]:
        """Execute a PUT request with X-Session-Token.

        Args:
            url: The full URL to PUT.
            body: JSON request body (optional).

        Returns:
            Parsed JSON response on success, None on non-auth errors.

        Raises:
            AuthFailedError: On 401/403.
        """
        self._check_auth_failed()

        try:
            response = requests.put(
                url,
                json=body,
                headers=self._headers,
                timeout=self._timeout,
            )
            return self._handle_response(response, url)
        except AuthFailedError:
            raise
        except requests.exceptions.RequestException as e:
            logger.warning("Network error on PUT %s: %s", url, e)
            return None

    # -------------------------------------------------------------------------
    # WebSocket Reconnect Logic
    # -------------------------------------------------------------------------

    def _attempt_reconnect(self) -> None:
        """Attempt WebSocket reconnect with exponential backoff.

        Tries up to 5 times with delays: 2s, 4s, 8s, 16s, 32s.
        On success, re-subscribes to topics.
        On failure after all attempts, logs and gives up.
        """
        if self._shutting_down or self._auth_failed:
            return

        for attempt in range(1, self._RECONNECT_MAX_ATTEMPTS + 1):
            if self._shutting_down:
                return

            delay = self._RECONNECT_BASE_DELAY * (2 ** (attempt - 1))
            logger.info(
                "WebSocket reconnect attempt %d/%d — waiting %.1fs",
                attempt,
                self._RECONNECT_MAX_ATTEMPTS,
                delay,
            )
            time.sleep(delay)

            if self._shutting_down:
                return

            try:
                self.connect_websocket()
                if self._player_id is not None:
                    self.subscribe(self._player_id)
                logger.info("WebSocket reconnected successfully on attempt %d", attempt)
                return
            except Exception as e:
                logger.warning(
                    "Reconnect attempt %d failed: %s", attempt, e
                )

        logger.error(
            "WebSocket reconnect failed after %d attempts — giving up",
            self._RECONNECT_MAX_ATTEMPTS,
        )

    def _on_disconnected(self) -> None:
        """Called by the STOMP listener when the connection is lost.

        Triggers reconnection in a background thread unless shutting down.
        """
        if self._shutting_down or self._auth_failed:
            return

        logger.warning("WebSocket connection lost — initiating reconnect")
        self._reconnect_thread = threading.Thread(
            target=self._attempt_reconnect, daemon=True
        )
        self._reconnect_thread.start()

    def _on_message(self, headers: Dict[str, str], body: str) -> None:
        """Called by the STOMP listener when a message is received.

        Routes the message to the appropriate registered handler based on
        the subscription destination.
        """
        import json

        destination = headers.get("destination", "")

        try:
            parsed = json.loads(body)
        except (json.JSONDecodeError, TypeError):
            logger.warning("Failed to parse STOMP message body: %s", body[:100])
            return

        # Route to registered handlers based on topic suffix
        for suffix, handler in self._message_handlers.items():
            if destination.endswith(f"/{suffix}"):
                try:
                    handler(parsed)
                except Exception as e:
                    logger.error("Handler error for topic '%s': %s", suffix, e)
                return

        # Check for game and leaderboard topics
        if destination.endswith("/game"):
            handler = self._message_handlers.get("game")
            if handler:
                try:
                    handler(parsed)
                except Exception as e:
                    logger.error("Handler error for 'game' topic: %s", e)
            return

        if destination.endswith("/leaderboard"):
            handler = self._message_handlers.get("leaderboard")
            if handler:
                try:
                    handler(parsed)
                except Exception as e:
                    logger.error("Handler error for 'leaderboard' topic: %s", e)
            return

        logger.debug("No handler for destination: %s", destination)

    # -------------------------------------------------------------------------
    # URL Parsing Helpers
    # -------------------------------------------------------------------------

    def _parse_ws_host(self) -> str:
        """Parse the WebSocket host from the backend base URL.

        Returns:
            Hostname string (e.g. 'localhost').
        """
        from urllib.parse import urlparse

        parsed = urlparse(self._base_url)
        return parsed.hostname or "localhost"

    def _parse_ws_port(self) -> int:
        """Parse the WebSocket port from the backend base URL.

        Returns:
            Port number (defaults to 8080 if not specified).
        """
        from urllib.parse import urlparse

        parsed = urlparse(self._base_url)
        if parsed.port:
            return parsed.port
        # Default to 8080 for the game backend
        return 8080


class _StompListener(stomp.ConnectionListener):
    """STOMP connection listener that delegates to BotClient methods."""

    def __init__(self, client: BotClient):
        self._client = client

    def on_message(self, frame) -> None:
        """Called when a STOMP MESSAGE frame is received."""
        headers = frame.headers if hasattr(frame, "headers") else {}
        body = frame.body if hasattr(frame, "body") else ""
        self._client._on_message(headers, body)

    def on_error(self, frame) -> None:
        """Called when a STOMP ERROR frame is received."""
        body = frame.body if hasattr(frame, "body") else ""
        logger.error("STOMP error frame received: %s", body[:200])

    def on_disconnected(self) -> None:
        """Called when the STOMP connection is lost."""
        self._client._on_disconnected()

    def on_connected(self, frame) -> None:
        """Called when the STOMP CONNECTED frame is received."""
        logger.debug("STOMP CONNECTED frame received")
