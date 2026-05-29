# Tasks — Backend (Java Spring Boot)

## Task 1: Project Initialisation

- [x] 1.1 Initialise Spring Boot 3.x project with Maven (dependencies: Spring Web, Spring Data JPA, Spring WebSocket, MySQL Connector, Lombok, Jackson, Bean Validation)
- [x] 1.2 Configure `application.yml` with MySQL connection, WebSocket config, game parameters (tick interval, max players, scoring weights)
- [x] 1.3 Set up package structure: config, controller, websocket, service, model, repository, dto, security, simulation, factory
- [x] 1.4 Configure Spring Actuator with readiness and liveness probe endpoints
- [x] 1.5 Set up structured logging (JSON format for production)

## Task 2: Database Schema and Entities

- [x] 2.1 Create Flyway/Liquibase migration for `game_session` table
- [x] 2.2 Create Flyway/Liquibase migration for `player` table
- [x] 2.3 Create Flyway/Liquibase migration for `basestation` table
- [x] 2.4 Create Flyway/Liquibase migration for `rapp_template` table
- [x] 2.5 Create Flyway/Liquibase migration for `rapp_deployment` table
- [x] 2.6 Create Flyway/Liquibase migration for `game_event` table
- [x] 2.7 Implement JPA entity: `GameSession` (id, sessionCode, state, hostPlayerId, maxPlayers, createdAt, startedAt, endedAt)
- [x] 2.8 Implement JPA entity: `Player` (id, sessionId, displayName, sessionToken, scoreMoney, scoreSatisfaction, scoreStability, compositeScore, connected)
- [x] 2.9 Implement JPA entity: `Basestation` (id, playerId, name, positionX, positionY, health, customerExperience, cost, energyEfficiency, automationReliability, slaCompliance)
- [x] 2.10 Implement JPA entity: `RappTemplate` (id, name, purpose, cost, benefit, risk, confidence, sideEffects, impact fields)
- [x] 2.11 Implement JPA entity: `RappDeployment` (id, templateId, basestationId, playerId, status, version, configuration, deployedAt)
- [x] 2.12 Implement JPA entity: `GameEvent` (id, sessionId, basestationId, eventType, severity, description, impact fields, resolved, createdAt, resolvedAt, escalationLevel)
- [x] 2.13 Implement Spring Data JPA repositories for all entities
- [x] 2.14 Create seed data migration for rApp catalogue (7 rApps with impact values from GAME_RULES.md)

## Task 3: Game Session Lifecycle

- [x] 3.1 Implement `GameSessionService.createSession(hostName)` — generates 8-char session code, creates session in LOBBY state, creates host player with token
- [x] 3.2 Implement `GameSessionService.joinSession(code, displayName)` — validates session exists, is in LOBBY state, has fewer than 6 players; creates player with token
- [x] 3.3 Implement `GameSessionService.startSession(code, token)` — validates host, verifies at least 2 players, transitions to ACTIVE, assigns basestations, starts tick engine
- [x] 3.4 Implement `GameSessionService.endSession(code)` — transitions to COMPLETED, calculates final scores, determines winner
- [x] 3.5 Implement session code generation (unique 8-character alphanumeric, collision-resistant)
- [x] 3.6 Implement `GameSessionController` with endpoints:
  - `POST /api/sessions` (create)
  - `POST /api/sessions/{code}/join`
  - `POST /api/sessions/{code}/start`
  - `GET /api/sessions/{code}`
- [x] 3.7 Implement DTOs: `CreateSessionRequest`, `JoinSessionRequest`, `SessionResponse`, `JoinResponse`

## Task 4: Player Management

- [x] 4.1 Implement `PlayerService.generateToken()` — creates unique 64-char session token
- [x] 4.2 Implement `PlayerService.validateToken(token)` — looks up player by token, returns player or throws UnauthorizedException
- [x] 4.3 Implement `PlayerService.disconnect(playerId)` — marks player as disconnected
- [x] 4.4 Implement `PlayerService.reconnect(token)` — restores full game state from database (basestations, deployed rApps, active events, scores)
- [x] 4.5 Implement `PlayerService.getPlayersBySession(sessionId)` — returns all players in a session

## Task 5: Basestation Management

- [x] 5.1 Implement `BasestationService.assignBasestations(sessionId)` — assigns 3 basestations per player with unique names and positions
- [x] 5.2 Implement `BasestationService.getPlayerBasestations(playerId)` — returns basestations with current metrics, deployed rApps, and active events
- [x] 5.3 Implement `BasestationService.updateMetrics(basestationId, metricDeltas)` — applies metric changes with clamping (0-100 for percentages, no negative)
- [x] 5.4 Implement `BasestationController` with endpoint: `GET /api/sessions/{code}/basestations`
- [x] 5.5 Implement DTOs: `BasestationResponse`, `MetricsDto`, `DeployedRappDto`, `ActiveEventDto`

## Task 6: rApp Catalogue and Factory

- [x] 6.1 Implement `RappCatalogueService.getCatalogue()` — returns all rApp templates
- [x] 6.2 Implement `RappFactory.createDeployment(templateId, basestationId, playerId)` — Factory pattern: creates RappDeployment from template with DEPLOYING status
- [x] 6.3 Implement `RappBehaviour` interface with method `calculateImpact(configuration): MetricImpact`
- [x] 6.4 Implement Strategy pattern classes for each rApp type:
  - `EnergySaverBehaviour`
  - `CapacityOptimiserBehaviour`
  - `FaultPredictorBehaviour`
  - `SlaGuardianBehaviour`
  - `ConfigDriftDetectorBehaviour`
  - `TrafficBalancerBehaviour`
  - `AlarmNoiseReducerBehaviour`
- [x] 6.5 Implement aggressiveness multiplier in each behaviour (LOW=0.5×, MODERATE=1.0×, HIGH=1.5×)
- [x] 6.6 Implement `CatalogueController` with endpoint: `GET /api/rapps/catalogue`
- [x] 6.7 Implement DTOs: `RappTemplateResponse`, `ImpactDto`

## Task 7: rApp Deployment and Lifecycle

- [x] 7.1 Implement `RappService.deploy(code, token, templateId, basestationId)` — validates ownership, deducts cost from player money, creates deployment in DEPLOYING status
- [x] 7.2 Implement `RappService.activate(deploymentId)` — transitions DEPLOYING → ACTIVE, applies impact to basestation metrics (called by tick engine after 1 tick)
- [x] 7.3 Implement `RappService.disable(code, token, deploymentId)` — validates ownership, removes impact from metrics, sets status to DISABLED
- [x] 7.4 Implement `RappService.tune(code, token, deploymentId, configuration)` — validates ownership, increments version, recalculates impact with new config
- [x] 7.5 Implement `RappService.rollback(code, token, deploymentId)` — validates version > 1, reverts to previous version/config, recalculates impact
- [x] 7.6 Implement State pattern for lifecycle transitions:
  - Valid: DEPLOYING → ACTIVE, ACTIVE → DISABLED, ACTIVE → ROLLING_BACK, ROLLING_BACK → ACTIVE
  - Invalid transitions throw IllegalStateException
- [x] 7.7 Implement Command pattern:
  - `DeployCommand` — encapsulates deploy action with validation
  - `TuneCommand` — encapsulates tune action with validation
  - `DisableCommand` — encapsulates disable action with validation
  - `RollbackCommand` — encapsulates rollback action with validation
- [x] 7.8 Implement conflict detection: check for conflicting rApp pairs on same basestation (Energy Saver + Capacity Optimiser, Fault Predictor + Alarm Noise Reducer, Traffic Balancer + Energy Saver)
- [x] 7.9 Implement side-effect application when conflicts detected (per GAME_RULES.md penalties)
- [x] 7.10 Implement `RappController` with endpoints:
  - `POST /api/sessions/{code}/rapps/deploy`
  - `PUT /api/sessions/{code}/rapps/{id}/tune`
  - `PUT /api/sessions/{code}/rapps/{id}/disable`
  - `PUT /api/sessions/{code}/rapps/{id}/rollback`
- [x] 7.11 Implement DTOs: `DeployRequest`, `TuneRequest`, `DeploymentResponse`

## Task 8: Event System

- [x] 8.1 Implement `EventService.createEvent(code, basestationId, eventType, severity, description, impact)` — creates GameEvent, notifies affected player via WebSocket
- [x] 8.2 Implement `EventService.resolveEvent(eventId)` — marks event as resolved, sets resolvedAt timestamp, stops per-tick impact
- [x] 8.3 Implement `EventService.escalateEvent(eventId)` — increments escalation level (max 3), applies escalation multiplier to impact
- [x] 8.4 Implement event resolution detection: when an effective rApp is deployed on an affected basestation, mark event for resolution after 1 tick
- [x] 8.5 Implement event auto-resolve: events at escalation level 3 for 5+ ticks auto-resolve with permanent -10 metric damage
- [x] 8.6 Implement event-rApp effectiveness mapping (per GAME_RULES.md resolution table)
- [x] 8.7 Implement internal API controller with endpoints:
  - `POST /api/internal/sessions/{code}/events`
  - `GET /api/internal/sessions/active`
- [x] 8.8 Implement internal API key validation filter (X-Internal-Key header)
- [x] 8.9 Implement DTOs: `CreateEventRequest`, `EventResponse`, `ActiveSessionsResponse`

## Task 9: Scoring and Leaderboard

- [x] 9.1 Implement `ScoreService.calculateCompositeScore(playerId)`:
  - money × 0.30 + customerSatisfaction × 0.35 + networkStability × 0.35
  - customerSatisfaction = average customerExperience across all player basestations
  - networkStability = average of (health + automationReliability + slaCompliance) / 3 across all basestations
- [x] 9.2 Implement `ScoreService.recalculateAllScores(sessionId)` — recalculates scores for all players in session
- [x] 9.3 Implement `ScoreService.getLeaderboard(sessionCode)` — returns players sorted by composite score descending
- [x] 9.4 Implement `ScoreService.determineWinner(sessionCode)` — returns player with highest score, applies tiebreakers (satisfaction > stability > money)
- [x] 9.5 Implement `LeaderboardController` with endpoint: `GET /api/sessions/{code}/leaderboard`
- [x] 9.6 Implement DTOs: `LeaderboardResponse`, `LeaderboardEntryDto`, `ScoreDto`

## Task 10: Game Tick Engine

- [x] 10.1 Implement `GameTickEngine` as a scheduled service (configurable interval from application.yml, default 5000ms)
- [x] 10.2 Implement tick processing per active session:
  1. Activate DEPLOYING rApps that have waited 1 tick
  2. Apply active rApp impacts to basestation metrics (using RappBehaviour strategy)
  3. Apply active event impacts to basestation metrics (with escalation multiplier)
  4. Apply side-effect penalties for conflicting rApp pairs
  5. Escalate unresolved events per escalation rate rules
  6. Check event resolution (effective rApp deployed → resolve after 1 tick)
  7. Auto-resolve events at max escalation for 5+ ticks
  8. Recalculate all player scores
  9. Increment tick counter, check game end condition (tick >= 60)
  10. Broadcast updates via WebSocket
- [x] 10.3 Implement metric clamping: percentage metrics clamped to [0.00, 100.00], cost has no upper limit
- [x] 10.4 Implement escalation rate logic:
  - LOW: +1 level per 3 ticks
  - MEDIUM: +1 level per 2 ticks
  - HIGH: +1 level per tick
  - CRITICAL: +1 level per tick
- [x] 10.5 Implement escalation impact multiplier: Level 0 = ×1.0, Level 1 = ×1.5, Level 2 = ×2.0, Level 3 = ×3.0
- [x] 10.6 Implement game end condition: transition session to COMPLETED when tick count reaches configured total (default 60)
- [x] 10.7 Implement tick counter persistence (store current tick in game_session or in-memory per session)

## Task 11: WebSocket Real-Time Communication

- [ ] 11.1 Configure Spring WebSocket with STOMP protocol (`/ws/game` endpoint)
- [ ] 11.2 Implement WebSocket authentication interceptor (validate X-Session-Token on connect)
- [ ] 11.3 Implement WebSocket session registry (track connected players per game session)
- [ ] 11.4 Implement server-to-client message types:
  - `GAME_STARTED` — broadcast to all players in session
  - `GAME_ENDED` — broadcast with final leaderboard and winner
  - `EVENT_OCCURRED` — targeted to affected player
  - `METRICS_UPDATED` — targeted to player whose basestation changed
  - `LEADERBOARD_UPDATED` — broadcast to all players in session
  - `RAPP_STATUS_CHANGED` — targeted to player whose rApp changed
- [ ] 11.5 Implement client-to-server `PLAYER_ACTION` message handling (validate, route to appropriate service)
- [ ] 11.6 Implement `ACTION_ERROR` response message for invalid WebSocket actions
- [ ] 11.7 Implement broadcast helper: `WebSocketBroadcaster.broadcastToSession(code, message)`
- [ ] 11.8 Implement targeted helper: `WebSocketBroadcaster.sendToPlayer(playerId, message)`
- [ ] 11.9 Implement disconnect handling (mark player as disconnected, allow reconnection)

## Task 12: Security and Access Control

- [ ] 12.1 Implement `SessionTokenFilter` — Spring filter that extracts X-Session-Token header, validates token, sets player context
- [ ] 12.2 Implement `@RequiresSessionMember` annotation or interceptor — verifies player belongs to the requested session
- [ ] 12.3 Implement ownership validation on all rApp/basestation endpoints (player can only modify their own resources)
- [ ] 12.4 Implement rejection of direct score/metrics modification (no endpoints accept score writes from clients)
- [ ] 12.5 Implement input validation with Bean Validation annotations on all request DTOs (@NotNull, @Size, @Min, @Max, @Pattern)
- [ ] 12.6 Implement global exception handler (`@ControllerAdvice`) that returns structured error responses without exposing internals
- [ ] 12.7 Implement internal API key validation for event generator endpoints (X-Internal-Key header check)

## Task 13: Testing

- [ ] 13.1 Write unit tests for `ScoreService` (score calculation, tiebreakers, determinism)
- [ ] 13.2 Write unit tests for `GameTickEngine` (metric accumulation, escalation, game end)
- [ ] 13.3 Write unit tests for `RappService` (deploy, tune, disable, rollback, conflict detection)
- [ ] 13.4 Write unit tests for `EventService` (creation, escalation, resolution, auto-resolve)
- [ ] 13.5 Write unit tests for `GameSessionService` (create, join, start validation, state transitions)
- [ ] 13.6 Write unit tests for each `RappBehaviour` strategy (impact calculation with aggressiveness multiplier)
- [ ] 13.7 Write unit tests for State pattern transitions (valid and invalid transitions)
- [ ] 13.8 Write API integration tests for all REST endpoints (happy path + error cases)
- [ ] 13.9 Write persistence integration tests (save/load entities, transaction rollback on failure)
- [ ] 13.10 Write WebSocket integration tests (connect, subscribe, receive broadcasts)
- [ ] 13.11 Write security tests (invalid token rejected, cross-session access denied, direct score modification rejected)
- [ ] 13.12 Write property-based tests:
  - Lobby player count invariant (0-6, start requires ≥2)
  - Basestation assignment non-overlap
  - rApp deploy-disable round trip (metrics return to original)
  - rApp rollback version revert
  - Score calculation determinism
  - Leaderboard ordering consistency
  - Event escalation monotonicity
  - Metrics independence per basestation
  - Input validation rejects invalid actions
  - Session access control
  - Score immutability from client
  - Tick-based metric accumulation
  - Game state persistence round trip
- [ ] 13.13 Write system test: full game flow (create session → join → start → deploy rApp → event fires → metrics change → score updates → game ends → winner determined)
