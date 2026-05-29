# Design Document

## Overview

rApp Tycoon is a browser-based multiplayer strategy game built with a microservices architecture. The system consists of four main components: a React frontend, a Java Spring Boot backend, a Python event generator, and a MySQL database. Players interact through the frontend, which communicates with the backend via REST APIs and WebSocket connections. The event generator runs as a sidecar service that pushes events to the backend. All services are containerised with Docker and deployed on Kubernetes.

## Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                        Kubernetes Cluster                         │
│                                                                   │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │   Frontend   │    │     Backend      │    │    Event      │  │
│  │   (React)    │◄──►│  (Spring Boot)   │◄───│  Generator    │  │
│  │   Pod(s)     │    │    Pod(s)        │    │  (Python)     │  │
│  └──────────────┘    └────────┬─────────┘    └───────────────┘  │
│                               │                                   │
│                        ┌──────┴──────┐                           │
│                        │    MySQL    │                            │
│                        │  Database   │                            │
│                        └─────────────┘                           │
└─────────────────────────────────────────────────────────────────┘
```

### Communication Patterns

- **Frontend ↔ Backend**: REST API for CRUD operations; WebSocket for real-time game state updates
- **Event Generator → Backend**: REST API calls to inject events into active game sessions
- **Backend → MySQL**: JDBC connection pool via Spring Data JPA

### Data Flow

1. Player actions (deploy, tune, disable, rollback) are sent via REST to the Backend
2. Backend validates, processes, and persists state changes to MySQL
3. Backend broadcasts state updates to all session players via WebSocket
4. Event Generator polls Backend for active sessions and pushes events via REST
5. Backend processes events, updates metrics, recalculates scores, and broadcasts updates

## Database Schema

### Tables

```sql
-- Game sessions
CREATE TABLE game_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_code VARCHAR(8) UNIQUE NOT NULL,
    state ENUM('LOBBY', 'ACTIVE', 'COMPLETED') NOT NULL DEFAULT 'LOBBY',
    host_player_id BIGINT,
    max_players INT NOT NULL DEFAULT 6,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL
);

-- Players
CREATE TABLE player (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    session_token VARCHAR(64) UNIQUE NOT NULL,
    score_money DECIMAL(10,2) DEFAULT 1000.00,
    score_satisfaction DECIMAL(5,2) DEFAULT 100.00,
    score_stability DECIMAL(5,2) DEFAULT 100.00,
    composite_score DECIMAL(10,2) DEFAULT 0.00,
    connected BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (session_id) REFERENCES game_session(id)
);

-- Basestations
CREATE TABLE basestation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    position_x INT NOT NULL,
    position_y INT NOT NULL,
    health DECIMAL(5,2) DEFAULT 100.00,
    customer_experience DECIMAL(5,2) DEFAULT 100.00,
    cost DECIMAL(10,2) DEFAULT 0.00,
    energy_efficiency DECIMAL(5,2) DEFAULT 100.00,
    automation_reliability DECIMAL(5,2) DEFAULT 100.00,
    sla_compliance DECIMAL(5,2) DEFAULT 100.00,
    FOREIGN KEY (player_id) REFERENCES player(id)
);

-- rApp catalogue (template definitions)
CREATE TABLE rapp_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    purpose TEXT NOT NULL,
    cost DECIMAL(10,2) NOT NULL,
    benefit TEXT NOT NULL,
    risk DECIMAL(5,2) NOT NULL,
    confidence DECIMAL(5,2) NOT NULL,
    side_effects TEXT,
    impact_health DECIMAL(5,2) DEFAULT 0.00,
    impact_customer_experience DECIMAL(5,2) DEFAULT 0.00,
    impact_cost DECIMAL(10,2) DEFAULT 0.00,
    impact_energy_efficiency DECIMAL(5,2) DEFAULT 0.00,
    impact_automation_reliability DECIMAL(5,2) DEFAULT 0.00,
    impact_sla_compliance DECIMAL(5,2) DEFAULT 0.00
);

-- Deployed rApps (instances on basestations)
CREATE TABLE rapp_deployment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    basestation_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    status ENUM('DEPLOYING', 'ACTIVE', 'DISABLED', 'ROLLING_BACK') NOT NULL,
    version INT NOT NULL DEFAULT 1,
    configuration JSON,
    deployed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (template_id) REFERENCES rapp_template(id),
    FOREIGN KEY (basestation_id) REFERENCES basestation(id),
    FOREIGN KEY (player_id) REFERENCES player(id)
);

-- Active events
CREATE TABLE game_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    basestation_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL,
    description TEXT,
    impact_health DECIMAL(5,2) DEFAULT 0.00,
    impact_customer_experience DECIMAL(5,2) DEFAULT 0.00,
    impact_cost DECIMAL(10,2) DEFAULT 0.00,
    impact_energy_efficiency DECIMAL(5,2) DEFAULT 0.00,
    impact_automation_reliability DECIMAL(5,2) DEFAULT 0.00,
    impact_sla_compliance DECIMAL(5,2) DEFAULT 0.00,
    resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    escalation_level INT DEFAULT 0,
    FOREIGN KEY (session_id) REFERENCES game_session(id),
    FOREIGN KEY (basestation_id) REFERENCES basestation(id)
);
```

## API Design

### REST Endpoints

#### Session Management
- `POST /api/sessions` — Create a new game session (returns session code)
- `POST /api/sessions/{code}/join` — Join a session by code
- `POST /api/sessions/{code}/start` — Start the game (host only)
- `GET /api/sessions/{code}` — Get session state

#### Player Actions
- `GET /api/sessions/{code}/basestations` — Get player's basestations
- `POST /api/sessions/{code}/rapps/deploy` — Deploy an rApp
- `PUT /api/sessions/{code}/rapps/{id}/tune` — Tune an rApp
- `PUT /api/sessions/{code}/rapps/{id}/disable` — Disable an rApp
- `PUT /api/sessions/{code}/rapps/{id}/rollback` — Roll back an rApp

#### Leaderboard
- `GET /api/sessions/{code}/leaderboard` — Get current leaderboard rankings

#### Catalogue
- `GET /api/rapps/catalogue` — Get available rApps

#### Events (Internal - Event Generator)
- `POST /api/internal/sessions/{code}/events` — Push a new event
- `GET /api/internal/sessions/active` — Get active sessions

#### Health
- `GET /actuator/health/readiness` — Readiness probe
- `GET /actuator/health/liveness` — Liveness probe

### WebSocket Messages

#### Server → Client
- `GAME_STARTED` — Game session has started
- `GAME_ENDED` — Game session has ended with final scores
- `EVENT_OCCURRED` — New event on a basestation
- `METRICS_UPDATED` — Basestation metrics changed
- `LEADERBOARD_UPDATED` — Score rankings changed
- `RAPP_STATUS_CHANGED` — rApp deployment status changed

#### Client → Server
- `PLAYER_ACTION` — Player performs an action (validated server-side)

## Component Design

### Backend (Java Spring Boot)

#### Package Structure
```
com.rapptycoon
├── config/          # Spring configuration, WebSocket config
├── controller/      # REST controllers
├── websocket/       # WebSocket handlers and message types
├── service/         # Business logic services
├── model/           # JPA entities
├── repository/      # Spring Data JPA repositories
├── dto/             # Data transfer objects
├── security/        # Session token validation, access control
├── simulation/      # Game tick engine, metrics calculation
└── factory/         # rApp creation (Factory pattern)
```

#### Key Services
- **GameSessionService** — Manages session lifecycle (lobby → active → completed)
- **PlayerService** — Handles player join/leave and state management
- **RappService** — rApp deployment, tuning, disable, rollback (Strategy pattern for behaviours)
- **MetricsService** — Calculates and updates network metrics per basestation
- **ScoreService** — Computes composite scores from metrics
- **EventService** — Processes incoming events and applies impacts
- **GameTickEngine** — Scheduled task that runs simulation ticks (updates metrics, escalates events)

#### Design Patterns
- **Strategy Pattern**: Each rApp type implements a `RappBehaviour` interface defining how it impacts metrics
- **Command Pattern**: Player actions are encapsulated as command objects for validation and execution
- **Factory Pattern**: `RappFactory` creates rApp instances from templates with proper initialisation
- **State Pattern**: `RappDeployment` uses state pattern for lifecycle transitions (deploying → active → disabled)

### Frontend (React)

#### Component Structure
```
src/
├── components/
│   ├── Lobby/           # Session creation, join, player list
│   ├── GameBoard/       # Main game view with map
│   ├── Basestation/     # Individual basestation display
│   ├── RappCatalogue/   # Available rApps browser
│   ├── Leaderboard/     # Score rankings
│   └── EventPanel/      # Active events display
├── hooks/
│   ├── useWebSocket.js  # WebSocket connection management
│   └── useGameState.js  # Game state management
├── services/
│   └── api.js           # REST API client
└── context/
    └── GameContext.js   # Global game state context
```

### Event Generator (Python)

```python
# Structure
event_generator/
├── main.py              # Entry point, scheduling loop
├── events.py            # Event type definitions and generation logic
├── client.py            # Backend API client
└── config.py            # Configuration (intervals, probabilities)
```

The Event Generator polls for active sessions and generates events based on configurable probabilities and intervals. Events are pushed to the Backend via REST API.

## Game Mechanics

### Tick Engine
- Default tick interval: 5 seconds
- Default game duration: 60 ticks (5 minutes)
- Each tick: apply rApp impacts, apply event impacts, escalate unresolved events, recalculate scores, check end condition

### Event Resolution
Events are resolved by deploying an effective rApp to the affected basestation:
- Power Outage → Energy Saver, Fault Predictor
- Traffic Spike → Capacity Optimiser, Traffic Balancer
- Hardware Failure → Fault Predictor, Config Drift Detector
- SLA Breach → SLA Guardian, Capacity Optimiser
- Interference → Traffic Balancer, Alarm Noise Reducer
- Capacity Overflow → Capacity Optimiser, Traffic Balancer

### rApp Conflict Detection
Certain rApp combinations on the same basestation produce side effects:
- Energy Saver + Capacity Optimiser → resource contention penalty
- Fault Predictor + Alarm Noise Reducer → alarm suppression penalty
- Traffic Balancer + Energy Saver → handover conflict penalty

The `RappService` checks for conflicts on each deployment and generates side-effect events when detected.

### Currency
All monetary values are in euros (€). Starting money: €1,000.

## Kubernetes Deployment

### Resources
- **backend-deployment**: 1-3 replicas, 512Mi-1Gi memory, 250m-500m CPU
- **frontend-deployment**: 1-2 replicas, 128Mi-256Mi memory, 100m-250m CPU
- **event-generator-deployment**: 1 replica, 256Mi-512Mi memory, 100m-250m CPU
- **mysql-statefulset**: 1 replica, 1Gi-2Gi memory, 500m-1000m CPU
- **Services**: ClusterIP for internal, LoadBalancer/Ingress for frontend
- **ConfigMap**: Game configuration (tick interval, max players, scoring weights)
- **Secret**: Database credentials, session signing key

## Correctness Properties

### Property 1: Lobby Player Count Invariant
- **Requirement**: 1.1, 1.2, 1.3
- **Property**: For any sequence of join operations on a Game_Session, the player count SHALL remain between 0 and 6 inclusive, and the game SHALL only start when the player count is at least 2
- **Type**: Invariant
- **Testable**: yes - property

### Property 2: Basestation Assignment Non-Overlap
- **Requirement**: 2.1
- **Property**: For any Game_Session with N players, each Basestation SHALL be assigned to exactly one Player, and no two Players share a Basestation
- **Type**: Invariant
- **Testable**: yes - property

### Property 3: rApp Deploy-Disable Round Trip
- **Requirement**: 3.2, 3.3
- **Property**: For any Basestation with initial metrics M, deploying an rApp and then disabling it SHALL return the Basestation's Network_Metrics to M
- **Type**: Round-trip
- **Testable**: yes - property

### Property 4: rApp Rollback Version Revert
- **Requirement**: 3.5
- **Property**: For any rApp at version V with configuration C, rolling back SHALL restore the rApp to version V-1 with its previous configuration
- **Type**: Round-trip
- **Testable**: yes - property

### Property 5: Score Calculation Determinism
- **Requirement**: 5.1, 5.2
- **Property**: For any given set of Network_Metrics values, the Score calculation SHALL always produce the same composite Score (idempotent)
- **Type**: Idempotence
- **Testable**: yes - property

### Property 6: Leaderboard Ordering Consistency
- **Requirement**: 5.5
- **Property**: For any set of Players with distinct Scores, the Leaderboard ordering SHALL be consistent with descending Score values (the Player with the highest Score is ranked first)
- **Type**: Invariant
- **Testable**: yes - property

### Property 7: Event Escalation Monotonicity
- **Requirement**: 4.4
- **Property**: For any unresolved Event, the escalation_level SHALL monotonically increase with each tick (never decrease while unresolved)
- **Type**: Metamorphic
- **Testable**: yes - property

### Property 8: Metrics Independence Per Basestation
- **Requirement**: 2.4
- **Property**: For any two Basestations A and B owned by the same Player, applying an rApp Impact to Basestation A SHALL not change Basestation B's Network_Metrics
- **Type**: Invariant
- **Testable**: yes - property

### Property 9: Input Validation Rejects Invalid Actions
- **Requirement**: 8.1, 8.4
- **Property**: For any player action with invalid input (out-of-range values, wrong types, missing fields), the Backend SHALL reject the action and return an error without modifying game state
- **Type**: Error condition
- **Testable**: yes - property

### Property 10: Session Access Control
- **Requirement**: 8.3
- **Property**: For any Player P and Game_Session S where P is not a member of S, all API requests from P to S SHALL be denied with an authorisation error
- **Type**: Invariant
- **Testable**: yes - property

### Property 11: Score Immutability From Client
- **Requirement**: 8.2
- **Property**: For any direct client request attempting to modify Score or Network_Metrics values, the Backend SHALL reject the request and the values SHALL remain unchanged
- **Type**: Invariant
- **Testable**: yes - property

### Property 12: Tick-Based Metric Accumulation
- **Requirement**: 10.2, 10.3
- **Property**: For a Basestation with one active rApp and one active Event over N ticks, the cumulative metric change SHALL equal N times the per-tick impact of the rApp plus N times the per-tick impact of the Event
- **Type**: Metamorphic
- **Testable**: yes - property

### Property 13: Game State Persistence Round Trip
- **Requirement**: 12.3
- **Property**: For any Player's game state saved to the database, restoring that state on reconnection SHALL produce a game view equivalent to the state at disconnection time
- **Type**: Round-trip
- **Testable**: yes - property

### Property 14: WebSocket Reconnection Backoff
- **Requirement**: 6.4
- **Property**: For any sequence of reconnection attempts, each successive delay SHALL be greater than or equal to the previous delay (exponential backoff)
- **Type**: Metamorphic
- **Testable**: yes - property

### Property 15: rApp Deployment Validation
- **Requirement**: 3.6
- **Property**: For any rApp deployment request missing Purpose, Cost, Benefit, Risk, or Confidence values, the Backend SHALL reject the deployment
- **Type**: Error condition
- **Testable**: yes - property
