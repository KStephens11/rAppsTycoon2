# Requirements Document

## Introduction

This document defines requirements for an AI Bot Player feature in rApp Tycoon. The feature allows the game host to add one or more automated bot players to a session before starting. Each bot joins the session as a normal player via the existing join API, receives its own basestations, and plays autonomously by reacting to WebSocket events and executing rApp actions (deploy, tune, disable, rollback).

Bot difficulty is calibrated through configurable response latency: easier bots wait longer before acting, harder bots react immediately. The core decision logic lives in a shared `strategy.py` module that can also power a future in-game recommendation system for human players.

Each bot runs as an independent Python pod on Kubernetes, following the same architectural patterns as the existing event-generator service.

---

## Glossary

- **Bot_Player**: An automated software agent that participates in a game session as a normal player, making rApp decisions without human input.
- **Bot_Pod**: A Kubernetes pod running the Bot Player Python service for a single bot instance in a specific game session.
- **Bot_Manager**: The Backend component responsible for provisioning, tracking, and communicating with Bot_Pods.
- **Strategy_Module**: The shared Python module (`strategy.py`) that scores available rApp actions against current game state and returns ranked recommendations.
- **Bot_Client**: The Python HTTP/WebSocket client in the Bot Player service that communicates with the Backend using `X-Session-Token` player authentication.
- **Difficulty_Level**: An enumeration (`EASY`, `MEDIUM`, `HARD`) that controls how quickly a Bot_Player reacts to events and conditions.
- **Response_Delay**: The configurable time in seconds that a Bot_Player waits before acting after observing a game state change. Defaults: EASY=10s, MEDIUM=5s, HARD=0s.
- **Session_Token**: The authentication token issued to a player (human or bot) upon joining a session, used in the `X-Session-Token` header for all subsequent API calls.
- **Recommendation**: A ranked suggestion produced by the Strategy_Module consisting of an rApp action, a target basestation, and a confidence score.
- **Recommender_Endpoint**: The backend endpoint `GET /api/sessions/{code}/recommendations` that returns Recommendation objects for the requesting player.
- **STOMP**: The messaging protocol used over WebSocket for real-time game events between the Backend and players.
- **rApp**: A network automation application that players deploy to basestations to affect metrics (health, customer experience, cost, energy efficiency, automation reliability, SLA compliance).
- **Composite_Score**: The weighted final score for a player: `(money × 0.30) + (customerSatisfaction × 0.35) + (networkStability × 0.35)`.

---

## Requirements

### Requirement 1: Host Adds Bot Players to a Lobby

**User Story:** As a game host, I want to add one or more bot players to my session lobby before starting, so that I can play against AI opponents without needing enough human players.

#### Acceptance Criteria

1. THE Backend SHALL expose an endpoint `POST /api/sessions/{code}/bots` that accepts a `count` (integer, 1–5) and a `difficulty` (`EASY`, `MEDIUM`, or `HARD`) in the request body.
2. WHEN the host calls `POST /api/sessions/{code}/bots`, THE Backend SHALL validate that the requesting player is the session host using the `X-Session-Token` header.
3. WHEN the host calls `POST /api/sessions/{code}/bots`, THE Backend SHALL validate that the session is in `LOBBY` state.
4. WHEN the host calls `POST /api/sessions/{code}/bots`, THE Backend SHALL validate that the total player count (existing players + requested bots) does not exceed 6.
5. WHEN the host calls `POST /api/sessions/{code}/bots` with valid parameters, THE Backend SHALL create the requested number of Bot_Player records in the database, each with a unique display name (e.g., `Bot-Alpha`, `Bot-Beta`) and `isBot: true`, before returning the response.
6. WHEN the host calls `POST /api/sessions/{code}/bots` with valid parameters, THE Backend SHALL return a `201 Created` response containing the list of created bot player identifiers and their assigned display names.
7. IF the session is not in `LOBBY` state, THEN THE Backend SHALL return a `409 Conflict` response with error code `INVALID_STATE`.
8. IF the requesting player is not the host, THEN THE Backend SHALL return a `403 Forbidden` response with error code `FORBIDDEN`.
9. IF the count parameter would cause the total player count to exceed 6, THEN THE Backend SHALL return a `409 Conflict` response with error code `SESSION_FULL`.
10. IF the count parameter is less than 1 or greater than 5, THEN THE Backend SHALL return a `400 Bad Request` response with error code `VALIDATION_ERROR`.
11. THE Backend SHALL include bot players in the session player list returned by `GET /api/sessions/{code}` with an `isBot: true` field so clients can identify bot entries.

---

### Requirement 2: Bot Player Joins and Authenticates

**User Story:** As a bot player, I want to join a session using the same API as a human player, so that the backend treats me as a standard participant with my own basestations and session token.

#### Acceptance Criteria

1. WHEN the game host starts the session, THE Backend SHALL call `POST /api/sessions/{code}/join` on behalf of each Bot_Player registered for that session, using the bot's assigned display name.
2. WHEN a Bot_Player joins the session, THE Backend SHALL issue a Session_Token to the Bot_Player in the same way it issues tokens to human players.
3. THE Backend SHALL assign 3 basestations to each Bot_Player at game start, using the same assignment logic applied to human players.
4. THE Bot_Manager SHALL store each Bot_Player's Session_Token and basestation assignments so the Bot_Pod can authenticate all subsequent API calls using the `X-Session-Token` header.
5. WHEN the game starts, THE Bot_Manager SHALL provision one Bot_Pod per bot player, passing the Session_Token, session code, and Difficulty_Level as environment variables.
6. IF a Bot_Pod fails to start within 30 seconds of game start, THEN THE Bot_Manager SHALL log the failure and continue the game without that bot's participation.

---

### Requirement 3: Bot Pod Architecture and Configuration

**User Story:** As a platform engineer, I want each bot to run as a separate Kubernetes pod following the same patterns as the event-generator service, so that bots are independently scalable and do not affect backend stability.

#### Acceptance Criteria

1. THE Bot_Pod SHALL be implemented in Python 3.11+ and structured in a `bot-player/` directory at the project root, following the same conventions as `event-generator/` (files: `main.py`, `client.py`, `config.py`, `strategy.py`, `requirements.txt`, `Dockerfile`).
2. THE Bot_Pod SHALL read all runtime parameters from environment variables: `SESSION_CODE`, `SESSION_TOKEN`, `DIFFICULTY` (EASY/MEDIUM/HARD), `BACKEND_BASE_URL`, and `LOG_LEVEL`.
3. IF any required environment variable (`SESSION_CODE`, `SESSION_TOKEN`, `BACKEND_BASE_URL`) is missing, THEN THE Bot_Pod SHALL log a descriptive error message, keep the `/health` endpoint available until the process fully terminates, and then exit with a non-zero exit code.
4. THE Bot_Pod SHALL support graceful shutdown on `SIGTERM` and `SIGINT` signals, completing any in-progress action before terminating.
5. THE Bot_Pod SHALL expose a `/health` HTTP endpoint on port 8081 that returns `200 OK` when the bot is operational, for use as a Kubernetes liveness probe.
6. THE Bot_Pod Dockerfile SHALL follow the same multi-stage build pattern as `event-generator/Dockerfile`.
7. THE Backend SHALL include a Kubernetes manifest template for Bot_Pod deployments in the `k8s/` directory.

---

### Requirement 4: Bot Client Authentication

**User Story:** As a bot player, I want my API client to authenticate using `X-Session-Token` just like a human player, so that I can call all the same game endpoints without special backend changes.

#### Acceptance Criteria

1. THE Bot_Client SHALL use the `X-Session-Token` header for all REST API requests, using the Session_Token obtained at join time.
2. THE Bot_Client SHALL use STOMP over WebSocket to subscribe to the player-specific event topics: `/topic/session/{code}/player/{playerId}/events`, `/topic/session/{code}/player/{playerId}/metrics`, and `/topic/session/{code}/player/{playerId}/rapps`.
3. THE Bot_Client SHALL authenticate the WebSocket connection using the `X-Session-Token` header in the STOMP CONNECT frame.
4. WHEN the Bot_Client receives a `401 Unauthorized` or `403 Forbidden` response from any REST endpoint, THE Bot_Client SHALL log the error and cease all further API calls for the remainder of the session. WHEN the Bot_Client receives any other error response (e.g., 500, network timeout), THE Bot_Client SHALL log the error and continue attempting calls to other endpoints.
5. WHEN the WebSocket connection is lost, THE Bot_Client SHALL attempt to reconnect up to 5 times with exponential backoff starting at 2 seconds before giving up.
6. THE Bot_Client code SHALL NOT use the `X-Internal-Key` header — that header is reserved for the event-generator service only.

---

### Requirement 5: Bot Reacts to Game Events

**User Story:** As a bot player, I want to detect and respond to game events on my basestations, so that I can act like a human player by deploying appropriate rApps to resolve threats.

#### Acceptance Criteria

1. WHEN the Bot_Client receives an `EVENT_OCCURRED` WebSocket message for one of its basestations, THE Bot_Pod SHALL invoke the Strategy_Module to compute a ranked list of Recommendations for that basestation.
2. WHEN the Bot_Client receives a `METRICS_UPDATED` WebSocket message, THE Bot_Pod SHALL update its internal game state to reflect the latest basestation metrics.
3. WHEN the Bot_Client receives a `RAPP_STATUS_CHANGED` WebSocket message, THE Bot_Pod SHALL update its internal tracking of deployed rApp statuses.
4. WHEN the Bot_Pod selects an action from the Strategy_Module output, THE Bot_Pod SHALL wait for the Response_Delay corresponding to its Difficulty_Level before issuing the API call.
5. WHEN the Response_Delay has elapsed, THE Bot_Pod SHALL execute the highest-ranked Recommendation by calling the appropriate REST endpoint (`POST /api/sessions/{code}/rapps/deploy`, `PUT /api/sessions/{code}/rapps/{id}/tune`, `PUT /api/sessions/{code}/rapps/{id}/disable`, or `PUT /api/sessions/{code}/rapps/{id}/rollback`).
6. WHEN the Bot_Client receives a `GAME_ENDED` WebSocket message, THE Bot_Pod SHALL stop issuing actions and initiate graceful shutdown.

---

### Requirement 6: Strategy Module Decision Logic

**User Story:** As a bot player (and future AI recommender), I want a shared strategy module that scores rApp actions against current game state, so that both the bot and the human recommender feature use consistent, maintainable decision logic.

#### Acceptance Criteria

1. THE Strategy_Module SHALL expose a function `rank_actions(game_state: GameState) -> list[Recommendation]` that accepts the current game state and returns a list of Recommendation objects sorted by descending score.
2. WHEN computing scores, THE Strategy_Module SHALL consider: active events on each basestation and their effective rApps (as defined in GAME_RULES), current metric values for each basestation, current money balance, and the rApp catalogue with deployment costs and per-tick impacts.
3. THE Strategy_Module SHALL score event-resolving actions higher than metric-improvement actions when an unresolved event is present on a basestation.
4. THE Strategy_Module SHALL apply a cost penalty to Recommendations that would reduce the player's remaining money below 200 (the minimum safe balance threshold).
5. THE Strategy_Module SHALL detect conflicting rApp pairs (Energy Saver + Capacity Optimiser, Fault Predictor + Alarm Noise Reducer, Traffic Balancer + Energy Saver) and reduce the score of any Recommendation that would create a new conflict on the target basestation.
6. EACH Recommendation SHALL contain: `action` (DEPLOY, TUNE, DISABLE, or ROLLBACK), `rapp_template_id` (for DEPLOY actions), `deployment_id` (for TUNE, DISABLE, ROLLBACK actions), `basestation_id`, `confidence` (float 0.0–1.0), and `reasoning` (human-readable string explaining the score).
7. THE Strategy_Module SHALL be importable from both the bot-player service and any future recommender service without modification.
8. THE Strategy_Module SHALL be stateless — THE Strategy_Module SHALL NOT store any mutable game state between calls.

---

### Requirement 7: Bot Difficulty Levels

**User Story:** As a game host, I want to choose the bot difficulty level, so that I can control how competitive the bot opponents are.

#### Acceptance Criteria

1. THE Bot_Pod SHALL support three Difficulty_Levels: `EASY`, `MEDIUM`, and `HARD`.
2. WHILE Difficulty_Level is `EASY`, THE Bot_Pod SHALL apply a Response_Delay of 10 seconds before executing any action.
3. WHILE Difficulty_Level is `MEDIUM`, THE Bot_Pod SHALL apply a Response_Delay of 5 seconds before executing any action.
4. WHILE Difficulty_Level is `HARD`, THE Bot_Pod SHALL apply a Response_Delay of less than 5 seconds (recommended: 0 seconds) before executing any action.
5. THE Bot_Pod SHALL apply the Response_Delay consistently across all action types (deploy, tune, disable, rollback) regardless of event severity.
6. IF the `DIFFICULTY` environment variable is set to an unrecognised value, THEN THE Bot_Pod SHALL default to `MEDIUM` difficulty and log a warning.

---

### Requirement 8: Bot Financial Constraints

**User Story:** As a bot player, I want to manage my money budget responsibly, so that I do not overspend and hurt my composite score.

#### Acceptance Criteria

1. THE Bot_Pod SHALL track its current money balance by initialising at €1,000.00 and subtracting each rApp deployment cost on successful deploy.
2. WHEN the Strategy_Module ranks actions, THE Bot_Pod SHALL pass the current money balance as part of the GameState so that the Strategy_Module can apply the cost penalty for low-balance situations.
3. IF the money balance minus the rApp deployment cost would result in a negative balance, THEN THE Bot_Pod SHALL not issue that deploy action.
4. THE Bot_Pod SHALL refresh its money balance from the leaderboard data received in `LEADERBOARD_UPDATED` WebSocket messages to correct any drift from the local estimate.

---

### Requirement 9: Game End and Bot Lifecycle

**User Story:** As a platform engineer, I want bot pods to terminate cleanly when a game session ends, so that Kubernetes resources are not wasted after a game finishes.

#### Acceptance Criteria

1. WHEN THE Bot_Pod receives a `GAME_ENDED` WebSocket message, THE Bot_Pod SHALL complete any pending Response_Delay action, then exit with code 0 within 5 seconds.
2. THE Bot_Manager SHALL monitor each Bot_Pod and issue a Kubernetes pod delete when the session transitions to `COMPLETED` state, as a fallback in case the Bot_Pod does not self-terminate.
3. IF a Bot_Pod crashes during a game, THE Kubernetes deployment SHALL NOT automatically restart the pod (restartPolicy: Never), so that a crashed bot does not re-enter a completed or replaced session.
4. THE Bot_Pod SHALL log a summary on shutdown that includes: total actions taken, final money balance, and session code.

---

### Requirement 10: Leaderboard and Score Visibility

**User Story:** As a human player, I want to see bot players on the leaderboard alongside human players, so that I know how I am performing relative to the AI opponents.

#### Acceptance Criteria

1. THE Backend SHALL include Bot_Player entries in all `LEADERBOARD_UPDATED` WebSocket broadcasts with the same score fields as human players.
2. THE Backend SHALL include an `isBot: true` field in each Bot_Player leaderboard entry so the frontend can optionally display a bot indicator.
3. THE Backend SHALL include Bot_Players in the `GET /api/sessions/{code}/leaderboard` REST response with the same structure as human player entries, plus the `isBot` field.
4. THE Backend SHALL apply the same scoring formula (`Composite_Score = (money × 0.30) + (customerSatisfaction × 0.35) + (networkStability × 0.35)`) to Bot_Players as to human players.

---

### Requirement 11: AI Recommender for Human Players (Stretch Goal)

**User Story:** As a human player, I want to request AI-powered rApp recommendations during a game, so that I can get strategic guidance without being forced to follow it.

#### Acceptance Criteria

1. THE Backend SHALL expose an endpoint `GET /api/sessions/{code}/recommendations` that the authenticated player can call during an active game.
2. WHEN THE Backend receives `GET /api/sessions/{code}/recommendations`, THE Backend SHALL invoke the Strategy_Module with the requesting player's current game state (basestations, metrics, active events, money balance).
3. WHEN the Strategy_Module returns results, THE Backend SHALL return up to 5 top-ranked Recommendation objects in the response, each including `action`, `rapp_template_id` or `deployment_id`, `basestation_id`, `confidence`, and `reasoning`.
4. THE Backend SHALL only return Recommendations to the player whose Session_Token matches the requesting player — THE Backend SHALL NOT return another player's recommendations.
5. WHEN a human player approves a Recommendation by submitting the action through the standard deploy/tune/disable/rollback endpoints, THE Backend SHALL process the action identically to a manually initiated action.
6. THE Backend SHALL not automatically execute any Recommendation — execution requires explicit player action.
7. IF the session is not in `ACTIVE` state, THEN THE Backend SHALL return a `409 Conflict` response with error code `INVALID_STATE`.

---

### Requirement 12: Observability and Logging

**User Story:** As a platform engineer, I want structured logs from bot pods, so that I can diagnose bot behaviour and performance issues without disrupting the game.

#### Acceptance Criteria

1. THE Bot_Pod SHALL emit structured log entries (JSON format) for each of the following events: WebSocket message received, Strategy_Module invocation and result, action submitted to the backend, and action response received.
2. THE Bot_Pod SHALL include `session_code`, `bot_display_name`, `difficulty`, and `timestamp` fields in every log entry.
3. WHEN the `LOG_LEVEL` environment variable is set to `DEBUG`, THE Bot_Pod SHALL include the full game state snapshot passed to the Strategy_Module in log entries.
4. WHEN the `LOG_LEVEL` environment variable is set to `INFO` or higher, THE Bot_Pod SHALL omit the full game state from log entries to avoid excessive log volume.
5. THE Bot_Pod SHALL NOT log the Session_Token value in any log entry at any log level.
