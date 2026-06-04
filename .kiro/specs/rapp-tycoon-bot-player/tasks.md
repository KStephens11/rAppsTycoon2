# Implementation Plan: rApp Tycoon Bot Player

## Overview

This implementation plan covers adding AI bot players to rApp Tycoon. The work spans four areas: (1) backend Java changes to expose a bot-adding endpoint and manage bot lifecycle, (2) a new Python `bot-player/` service that autonomously plays the game, (3) Kubernetes manifests for bot pod provisioning, and (4) Docker/compose updates for local development. Tasks are ordered so each step builds on the previous, with property-based tests placed close to the code they validate.

## Tasks

- [x] 1. Backend data model and DTO changes
  - [x] 1.1 Add `isBot` and `difficulty` fields to the Player entity and database schema
    - Add `isBot` (boolean, default false) and `difficulty` (varchar, nullable) columns to the `Player` JPA entity
    - Create a Flyway/Liquibase migration (or schema.sql update) adding the columns to the `player` table
    - Update `PlayerDto` to include `isBot` field in session and leaderboard responses
    - _Requirements: 1.5, 1.11, 10.2_

  - [x] 1.2 Create Bot-related DTOs and Difficulty enum
    - Create `AddBotsRequest` record with `count` (1–5, validated) and `difficulty` (EASY/MEDIUM/HARD)
    - Create `Difficulty` enum in model package
    - Create `AddBotsResponse` record containing `List<BotPlayerDto>`
    - Create `BotPlayerDto` record with `id`, `displayName`, `isBot` fields
    - _Requirements: 1.1, 1.6_

- [x] 2. Backend Bot Controller and Service
  - [x] 2.1 Implement `BotController` with `POST /api/sessions/{code}/bots` endpoint
    - Create `BotController` class in `controller` package with the endpoint
    - Validate `X-Session-Token` to confirm requesting player is the session host
    - Validate session is in LOBBY state (return 409 if not)
    - Validate total player count + requested bots ≤ 6 (return 409 SESSION_FULL if exceeded)
    - Validate count is 1–5 (return 400 VALIDATION_ERROR if not)
    - Create bot Player records with sequential names (Bot-Alpha, Bot-Beta, etc.) and `isBot=true`
    - Return 201 with the list of created bot players
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_

  - [ ]* 2.2 Write unit tests for BotController
    - Test host validation (403 for non-host)
    - Test LOBBY state validation (409 for non-LOBBY)
    - Test player count cap enforcement (409 SESSION_FULL)
    - Test count range validation (400 for invalid count)
    - Test successful bot creation with correct names and isBot flag
    - _Requirements: 1.1–1.11_

  - [x] 2.3 Update `GET /api/sessions/{code}` and leaderboard responses to include `isBot` field
    - Modify `buildSessionResponse` in `GameSessionService` to include `isBot` in PlayerDto
    - Modify leaderboard DTOs and service to include `isBot` field
    - Ensure bot players appear in leaderboard with same scoring formula
    - _Requirements: 1.11, 10.1, 10.2, 10.3, 10.4_

- [x] 3. Checkpoint - Backend bot endpoint complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Backend Bot Manager component
  - [x] 4.1 Implement `BotManager` interface and `BotManagerImpl` service
    - Create `BotManager` interface with `provisionBots(String sessionCode)` and `cleanupBots(String sessionCode)` methods
    - Implement `BotManagerImpl` that: joins each bot via the existing join logic (issuing tokens, assigning basestations), stores tokens, and creates K8s pods via the Kubernetes Java client
    - Pass `SESSION_CODE`, `SESSION_TOKEN`, `DIFFICULTY`, `BACKEND_BASE_URL` as pod environment variables
    - Log and continue if pod creation fails within 30 seconds
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 4.2 Integrate BotManager into game start and end flows
    - Call `botManager.provisionBots(code)` in `GameSessionService.startSession()` after basestations are assigned
    - Call `botManager.cleanupBots(code)` in `GameSessionService.endSession()` as fallback pod cleanup
    - _Requirements: 2.5, 9.2_

  - [ ]* 4.3 Write unit tests for BotManager
    - Test bot joining and token storage
    - Test K8s pod creation with correct env vars (mock K8s client)
    - Test graceful handling of pod startup failure
    - Test cleanup on session end
    - _Requirements: 2.1–2.6, 9.2_

  - [x] 4.4 Add Kubernetes Java Client dependency to `pom.xml`
    - Add `io.fabric8:kubernetes-client` dependency with appropriate version
    - _Requirements: 2.5_

- [x] 5. Checkpoint - Backend BotManager complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Bot Player service - Config and Models
  - [x] 6.1 Create `bot-player/` directory structure and `config.py`
    - Create `bot-player/` directory at project root with empty `__init__.py`
    - Implement `config.py` with `BotConfig` dataclass, `Difficulty` enum, `RESPONSE_DELAYS` mapping
    - Implement `BotConfig.from_env()` that reads `SESSION_CODE`, `SESSION_TOKEN`, `DIFFICULTY`, `BACKEND_BASE_URL`, `LOG_LEVEL`, `HEALTH_PORT`
    - Raise `ValueError` with descriptive message if required vars are missing
    - Default `DIFFICULTY` to MEDIUM with warning if unrecognised value
    - _Requirements: 3.1, 3.2, 3.3, 7.1, 7.6_

  - [x] 6.2 Create `models.py` with game state data classes
    - Implement `BasestationState` dataclass (id, name, metrics, deployed_rapps, active_events)
    - Implement `GameState` dataclass (basestations, money, catalogue, difficulty)
    - Implement `Recommendation` dataclass (action, rapp_template_id, deployment_id, basestation_id, confidence, reasoning)
    - _Requirements: 6.6_

  - [ ]* 6.3 Write unit tests for config parsing
    - Test successful parsing with all env vars set
    - Test missing required var raises ValueError
    - Test unrecognised DIFFICULTY defaults to MEDIUM
    - Test response_delay property returns correct values per difficulty
    - _Requirements: 3.2, 3.3, 7.6_

- [x] 7. Bot Player service - Strategy Module
  - [x] 7.1 Implement `strategy.py` with `rank_actions()` function
    - Implement event-resolving scoring: map event types to effective rApps per GAME_RULES
    - Implement metric-improvement scoring: evaluate catalogue rApps against basestation metrics
    - Implement cost penalty: reduce confidence when money would drop below 200
    - Implement conflict detection: reduce confidence for conflicting rApp pairs
    - Implement TUNE/DISABLE scoring for underperforming deployed rApps
    - Sort recommendations by confidence descending, return list
    - Ensure module is stateless (no mutable module-level state)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8_

  - [ ]* 7.2 Write property test: Strategy scoring determinism (Property 1)
    - **Property 1: Strategy scoring determinism (statelessness)**
    - For any GameState, `rank_actions(game_state)` called multiple times produces identical results
    - **Validates: Requirements 6.1, 6.8**

  - [ ]* 7.3 Write property test: Event-resolving priority (Property 2)
    - **Property 2: Event-resolving actions always outrank metric-improvement actions**
    - For any GameState with active events, event-resolving recommendations have higher confidence than metric-only ones for the same basestation
    - **Validates: Requirement 6.3**

  - [ ]* 7.4 Write property test: Cost penalty monotonicity (Property 3)
    - **Property 3: Cost penalty monotonicity**
    - For two identical GameStates differing only in money (A < B), DEPLOY confidence in state A ≤ confidence in state B
    - **Validates: Requirements 6.4, 8.1**

  - [ ]* 7.5 Write property test: Conflict detection reduces confidence (Property 4)
    - **Property 4: Conflict detection reduces confidence**
    - Deploying a conflicting rApp on a basestation produces lower confidence than on an otherwise-identical basestation without the conflict
    - **Validates: Requirement 6.5**

  - [ ]* 7.6 Write property test: Bot never deploys with insufficient funds (Property 5)
    - **Property 5: Bot never deploys with insufficient funds**
    - When money < rApp cost, confidence for that DEPLOY action is 0 or action is excluded
    - **Validates: Requirement 8.3**

  - [ ]* 7.7 Write property test: Recommendation field validity (Property 8)
    - **Property 8: Recommendation contains all required fields**
    - Every Recommendation has valid action, non-negative basestation_id, confidence in [0,1], non-empty reasoning; DEPLOY has rapp_template_id set, TUNE/DISABLE/ROLLBACK has deployment_id set
    - **Validates: Requirement 6.6**

- [x] 8. Checkpoint - Strategy module complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Bot Player service - Client
  - [x] 9.1 Implement `client.py` with REST and WebSocket methods
    - Implement `BotClient` class with `X-Session-Token` header on all requests
    - Implement REST methods: `get_basestations()`, `deploy_rapp()`, `tune_rapp()`, `disable_rapp()`, `rollback_rapp()`, `get_catalogue()`
    - Implement STOMP WebSocket connection with `X-Session-Token` in CONNECT frame
    - Implement `subscribe(player_id)` to player-specific topics (events, metrics, rapps)
    - Implement `disconnect()` for graceful WebSocket teardown
    - Handle 401/403 by logging and ceasing all further calls
    - Handle other errors (500, timeout) by logging and continuing
    - Implement exponential backoff WebSocket reconnect (2s, 4s, 8s, 16s, 32s — 5 attempts)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [ ]* 9.2 Write unit tests for BotClient
    - Test X-Session-Token header is included on all requests (mock HTTP)
    - Test X-Internal-Key is never included
    - Test 401/403 handling ceases further calls
    - Test other error codes allow continued operation
    - Test WebSocket reconnect backoff logic
    - _Requirements: 4.1–4.6_

- [x] 10. Bot Player service - Main lifecycle
  - [x] 10.1 Implement `main.py` with bot lifecycle and health endpoint
    - Load config via `BotConfig.from_env()`, exit non-zero if invalid
    - Set up structured JSON logging with session_code, bot_display_name, difficulty, timestamp
    - Ensure Session_Token is never logged at any level
    - Start `/health` HTTP endpoint on configured port (default 8081)
    - Connect WebSocket, subscribe to player topics
    - Implement event loop: on `EVENT_OCCURRED` invoke strategy, apply response delay, execute top action
    - On `METRICS_UPDATED`: update internal game state
    - On `RAPP_STATUS_CHANGED`: update deployed rApp tracking
    - On `LEADERBOARD_UPDATED`: refresh money balance
    - On `GAME_ENDED`: complete pending action, log summary, exit(0) within 5 seconds
    - Handle `SIGTERM`/`SIGINT` for graceful shutdown
    - Track money balance (init €1000, subtract on deploy, refresh from leaderboard)
    - Skip deploy actions that would result in negative balance
    - _Requirements: 3.3, 3.4, 3.5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 7.2, 7.3, 7.4, 7.5, 8.1, 8.2, 8.3, 8.4, 9.1, 9.4, 12.1, 12.2, 12.3, 12.4, 12.5_

  - [ ]* 10.2 Write unit tests for main lifecycle
    - Test config failure results in non-zero exit
    - Test GAME_ENDED triggers shutdown within 5 seconds
    - Test SIGTERM triggers graceful shutdown
    - Test response delay is applied per difficulty level
    - Test money tracking prevents negative balance deploys
    - _Requirements: 3.3, 3.4, 5.6, 7.2–7.5, 8.3, 9.1_

- [x] 11. Checkpoint - Bot Player Python service complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Packaging and deployment
  - [x] 12.1 Create `bot-player/requirements.txt`
    - Add pinned dependencies: requests, stomp.py, flask (health endpoint), pytest, hypothesis, responses
    - _Requirements: 3.1_

  - [x] 12.2 Create `bot-player/Dockerfile` following event-generator multi-stage pattern
    - Stage 1: install dependencies from requirements.txt
    - Stage 2: copy source files, set non-root user, set ENTRYPOINT
    - _Requirements: 3.6_

  - [x] 12.3 Create Kubernetes pod manifest template in `k8s/bot-player-pod.yaml`
    - Define pod template with env vars (SESSION_CODE, SESSION_TOKEN, DIFFICULTY, BACKEND_BASE_URL)
    - Set `restartPolicy: Never` so crashed bots don't re-enter sessions
    - Add liveness probe on `/health:8081`
    - Add resource limits (64Mi memory, 100m CPU)
    - Run as non-root user
    - _Requirements: 3.5, 3.7, 9.3_

  - [x] 12.4 Update `docker-compose.yml` to include bot-player service definition
    - Add `bot-player` service entry with build context, environment variables, and dependency on backend
    - _Requirements: 3.1_

- [x] 13. Recommender endpoint (Stretch Goal)
  - [x] 13.1 Implement `GET /api/sessions/{code}/recommendations` endpoint
    - Create endpoint in a new `RecommenderController` or extend `BotController`
    - Validate session is ACTIVE (return 409 if not)
    - Gather requesting player's game state (basestations, metrics, events, money)
    - Invoke strategy module (call Python subprocess or embed via GraalPy)
    - Return top 5 Recommendations with action, target, confidence, reasoning
    - Ensure recommendations are scoped to requesting player's token only
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

  - [ ]* 13.2 Write unit tests for Recommender endpoint
    - Test 409 for non-ACTIVE session
    - Test recommendations are scoped to requesting player
    - Test response contains up to 5 recommendations with required fields
    - _Requirements: 11.1–11.7_

- [x] 14. Final checkpoint - All components integrated
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The bot-player service follows event-generator patterns (config.py, client.py, main.py structure)
- Backend uses existing Spring Boot patterns (controller → service → repository)
- The recommender endpoint (task 13) is a stretch goal and can be deferred

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "6.1", "6.2"] },
    { "id": 1, "tasks": ["2.1", "2.3", "6.3", "7.1"] },
    { "id": 2, "tasks": ["2.2", "4.4", "7.2", "7.3", "7.4", "7.5", "7.6", "7.7"] },
    { "id": 3, "tasks": ["4.1", "9.1", "12.1"] },
    { "id": 4, "tasks": ["4.2", "4.3", "9.2", "12.2", "12.3", "12.4"] },
    { "id": 5, "tasks": ["10.1"] },
    { "id": 6, "tasks": ["10.2", "13.1"] },
    { "id": 7, "tasks": ["13.2"] }
  ]
}
```
