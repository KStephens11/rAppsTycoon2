# Tasks

## Task 1: Project Setup and Infrastructure

- [ ] 1.1 Initialise Spring Boot project with dependencies (Spring Web, Spring Data JPA, Spring WebSocket, MySQL Connector, Lombok, Jackson)
- [ ] 1.2 Create MySQL database schema with all tables (game_session, player, basestation, rapp_template, rapp_deployment, game_event)
- [ ] 1.3 Configure Spring Data JPA entities and repositories for all tables
- [ ] 1.4 Set up application.yml with database connection, WebSocket config, and game parameters
- [ ] 1.5 Create Dockerfile for Backend service
- [ ] 1.6 Initialise React project with dependencies (React, React Router, WebSocket client)
- [ ] 1.7 Create Dockerfile for Frontend service
- [ ] 1.8 Initialise Python event generator project with dependencies (requests, schedule)
- [ ] 1.9 Create Dockerfile for Event Generator service
- [ ] 1.10 Create Docker Compose file for local development (backend, frontend, event-generator, mysql)
- [ ] 1.11 Create Kubernetes manifests (deployments, services, ConfigMap, Secret, readiness/liveness probes)

## Task 2: Game Session Lifecycle

- [ ] 2.1 Implement GameSession entity and GameSessionRepository
- [ ] 2.2 Implement GameSessionService with create, join, start, and end logic
- [ ] 2.3 Implement GameSessionController with REST endpoints (POST /api/sessions, POST /api/sessions/{code}/join, POST /api/sessions/{code}/start, GET /api/sessions/{code})
- [ ] 2.4 Implement session code generation (unique 8-character alphanumeric)
- [ ] 2.5 Implement lobby player count validation (reject joins when full at 6 players, reject start when fewer than 2 players)
- [ ] 2.6 Implement game state transitions (LOBBY → ACTIVE → COMPLETED)
- [ ] 2.7 Write property test: Lobby Player Count Invariant (Property 1)
  - Generate random sequences of join requests and verify player count stays between 0 and 6, and verify game start is rejected with fewer than 2 players

## Task 3: Player Management

- [ ] 3.1 Implement Player entity and PlayerRepository
- [ ] 3.2 Implement PlayerService with join, disconnect, and reconnect logic
- [ ] 3.3 Implement session token generation and validation for player authentication
- [ ] 3.4 Implement player state restoration on reconnection from database
- [ ] 3.5 Write property test: Session Access Control (Property 10)
  - Generate random player/session combinations and verify non-members are denied access

## Task 4: Basestation Management

- [ ] 4.1 Implement Basestation entity and BasestationRepository
- [ ] 4.2 Implement BasestationService with assignment logic (assign basestations to players at game start)
- [ ] 4.3 Implement Network_Metrics tracking per basestation (health, customer_experience, cost, energy_efficiency, automation_reliability, sla_compliance)
- [ ] 4.4 Implement GET /api/sessions/{code}/basestations endpoint
- [ ] 4.5 Write property test: Basestation Assignment Non-Overlap (Property 2)
  - Generate random player counts (1-6) and verify each basestation is assigned to exactly one player
- [ ] 4.6 Write property test: Metrics Independence Per Basestation (Property 8)
  - Apply rApp impact to one basestation and verify other basestations' metrics are unchanged

## Task 5: rApp Catalogue and Factory

- [ ] 5.1 Implement RappTemplate entity and RappTemplateRepository
- [ ] 5.2 Seed rApp catalogue data (Energy Saver, Capacity Optimiser, Fault Predictor, SLA Guardian, Configuration Drift Detector, Traffic Balancer, Alarm Noise Reducer)
- [ ] 5.3 Implement RappFactory using Factory pattern to create rApp instances from templates
- [ ] 5.4 Implement RappBehaviour interface and Strategy pattern implementations for each rApp type
- [ ] 5.5 Implement GET /api/rapps/catalogue endpoint

## Task 6: rApp Deployment and Lifecycle

- [ ] 6.1 Implement RappDeployment entity and RappDeploymentRepository
- [ ] 6.2 Implement RappService with deploy, tune, disable, and rollback operations
- [ ] 6.3 Implement State pattern for rApp lifecycle transitions (DEPLOYING → ACTIVE → DISABLED, ROLLING_BACK)
- [ ] 6.4 Implement Command pattern for player actions (DeployCommand, TuneCommand, DisableCommand, RollbackCommand)
- [ ] 6.5 Implement deployment validation (require Purpose, Cost, Benefit, Risk, Confidence)
- [ ] 6.6 Implement rApp impact application to basestation metrics on deployment completion
- [ ] 6.7 Implement rApp impact removal on disable
- [ ] 6.8 Implement rApp rollback to previous version
- [ ] 6.9 Implement side-effect detection for rApp combinations
- [ ] 6.10 Implement REST endpoints (POST deploy, PUT tune, PUT disable, PUT rollback)
- [ ] 6.11 Write property test: rApp Deploy-Disable Round Trip (Property 3)
  - Deploy an rApp then disable it and verify metrics return to original values
- [ ] 6.12 Write property test: rApp Rollback Version Revert (Property 4)
  - Deploy, tune (creating new version), then rollback and verify previous version is restored
- [ ] 6.13 Write property test: rApp Deployment Validation (Property 15)
  - Generate deployment requests with missing fields and verify all are rejected

## Task 7: Event System

- [ ] 7.1 Implement GameEvent entity and GameEventRepository
- [ ] 7.2 Implement EventService with event creation, assignment, and resolution logic
- [ ] 7.3 Implement event escalation logic (increase severity for unresolved events each tick)
- [ ] 7.4 Implement internal REST endpoints (POST /api/internal/sessions/{code}/events, GET /api/internal/sessions/active)
- [ ] 7.5 Implement Python Event Generator main loop (poll active sessions, generate events)
- [ ] 7.6 Implement event type definitions in Python (power outage, traffic spike, hardware failure, SLA breach)
- [ ] 7.7 Implement configurable event generation intervals and probabilities
- [ ] 7.8 Write property test: Event Escalation Monotonicity (Property 7)
  - Simulate multiple ticks on an unresolved event and verify escalation_level never decreases

## Task 8: Scoring and Leaderboard

- [ ] 8.1 Implement ScoreService with weighted composite score calculation (money, satisfaction, stability)
- [ ] 8.2 Implement score recalculation triggered by metrics changes
- [ ] 8.3 Implement leaderboard ranking logic (sort players by composite score descending)
- [ ] 8.4 Implement winner determination at game end (highest composite score)
- [ ] 8.5 Write property test: Score Calculation Determinism (Property 5)
  - Generate random metrics values and verify same inputs always produce same score
- [ ] 8.6 Write property test: Leaderboard Ordering Consistency (Property 6)
  - Generate random player scores and verify ordering is consistent with descending score values

## Task 9: Game Tick Engine and Metrics Simulation

- [ ] 9.1 Implement GameTickEngine as a scheduled service (configurable tick interval)
- [ ] 9.2 Implement per-tick metric updates for all active basestations
- [ ] 9.3 Implement rApp impact accumulation per tick (apply active rApp effects)
- [ ] 9.4 Implement event impact accumulation per tick (apply active event negative effects)
- [ ] 9.5 Implement combined impact calculation for multiple rApps on same basestation (with interaction effects)
- [ ] 9.6 Implement event escalation per tick for unresolved events
- [ ] 9.7 Implement game end condition check per tick
- [ ] 9.8 Write property test: Tick-Based Metric Accumulation (Property 12)
  - Simulate N ticks with one rApp and one event and verify cumulative change equals N * per-tick impacts

## Task 10: WebSocket Real-Time Communication

- [ ] 10.1 Configure Spring WebSocket with STOMP protocol
- [ ] 10.2 Implement WebSocket session management (connect, disconnect, track active connections)
- [ ] 10.3 Implement server-to-client message types (GAME_STARTED, GAME_ENDED, EVENT_OCCURRED, METRICS_UPDATED, LEADERBOARD_UPDATED, RAPP_STATUS_CHANGED)
- [ ] 10.4 Implement client-to-server message validation (reject invalid messages)
- [ ] 10.5 Implement broadcast logic for leaderboard updates to all session players
- [ ] 10.6 Implement targeted event notifications to affected players
- [ ] 10.7 Write property test: Input Validation Rejects Invalid Actions (Property 9)
  - Generate random invalid WebSocket messages and verify all are rejected without state changes

## Task 11: Security and Access Control

- [ ] 11.1 Implement session token-based authentication filter
- [ ] 11.2 Implement game session membership validation on all endpoints
- [ ] 11.3 Implement server-side validation for all player action requests
- [ ] 11.4 Implement rejection of direct score/metrics modification attempts
- [ ] 11.5 Implement input validation (type checking, range validation, format validation)
- [ ] 11.6 Implement error responses that do not expose internal system details
- [ ] 11.7 Write property test: Score Immutability From Client (Property 11)
  - Generate random requests attempting to modify scores directly and verify all are rejected

## Task 12: Frontend - Lobby and Session

- [ ] 12.1 Implement Lobby component (create session, join by code, display player list)
- [ ] 12.2 Implement session state display (waiting, active, completed)
- [ ] 12.3 Implement game start button for host player
- [ ] 12.4 Implement API service module for REST calls

## Task 13: Frontend - Game Board and Basestations

- [ ] 13.1 Implement GameBoard component with map view showing player's basestations
- [ ] 13.2 Implement Basestation component showing status, metrics, and active events
- [ ] 13.3 Implement visual indicators for events on affected basestations
- [ ] 13.4 Implement EventPanel component showing active events with details

## Task 14: Frontend - rApp Management

- [ ] 14.1 Implement RappCatalogue component displaying available rApps with characteristics
- [ ] 14.2 Implement rApp deployment UI (select basestation, select rApp, confirm)
- [ ] 14.3 Implement rApp management actions (tune, disable, rollback) on deployed rApps
- [ ] 14.4 Implement rApp status indicators (deploying, active, disabled, rolling back)

## Task 15: Frontend - Leaderboard and WebSocket

- [ ] 15.1 Implement Leaderboard component showing all players' ranks, names, and score components
- [ ] 15.2 Implement useWebSocket hook with connection management and exponential backoff reconnection
- [ ] 15.3 Implement useGameState hook for managing game state from WebSocket messages
- [ ] 15.4 Implement GameContext provider for global game state
- [ ] 15.5 Write property test: WebSocket Reconnection Backoff (Property 14)
  - Simulate connection failures and verify each successive retry delay is greater than or equal to the previous

## Task 16: Frontend - Game End

- [ ] 16.1 Implement game end screen showing final scores and winner
- [ ] 16.2 Implement game state transition handling (lobby → active → completed)

## Task 17: Integration and Persistence

- [ ] 17.1 Implement database transaction management for related state changes
- [ ] 17.2 Implement player state restoration on reconnection (full game state from database)
- [ ] 17.3 Implement game state persistence for all player actions and event outcomes
- [ ] 17.4 Write property test: Game State Persistence Round Trip (Property 13)
  - Save player state, simulate reconnection, and verify restored state matches original

## Task 18: Testing and Quality

- [ ] 18.1 Write unit tests for ScoreService, MetricsService, and GameTickEngine
- [ ] 18.2 Write API integration tests for all REST endpoints
- [ ] 18.3 Write frontend component tests for Lobby, GameBoard, Leaderboard
- [ ] 18.4 Write Python unit tests for event generation logic
- [ ] 18.5 Configure SonarQube integration for code quality analysis
- [ ] 18.6 Write system test for full game flow (create session, join, start, play, end)
