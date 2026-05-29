# rApp Tycoon — Project Architecture

## Overview

rApp Tycoon is a browser-based multiplayer strategy game where 1-6 players per session deploy and manage simplified rApps to optimise a virtual 5G network. The system uses a simple microservices architecture with four components deployed on Kubernetes.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Kubernetes Cluster                               │
│                                                                           │
│  ┌────────────────┐       ┌─────────────────────┐     ┌──────────────┐  │
│  │   Frontend     │       │      Backend        │     │    Event     │  │
│  │   (React)      │◄─────►│   (Spring Boot)     │◄────│  Generator   │  │
│  │                │ REST/ │                     │REST │  (Python)    │  │
│  │   Nginx        │ WS    │   Game Logic        │     │              │  │
│  │   Pod(s)       │       │   Pod(s)            │     │  Pod (1)     │  │
│  └────────────────┘       └──────────┬──────────┘     └──────────────┘  │
│                                      │                                    │
│                               ┌──────┴──────┐                            │
│                               │    MySQL    │                             │
│                               │  StatefulSet│                             │
│                               │  (1 replica)│                             │
│                               └─────────────┘                            │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Supporting Resources                                                │ │
│  │  • ConfigMap: game config (tick interval, scoring weights, etc.)     │ │
│  │  • Secret: DB credentials, session signing key                       │ │
│  │  • Ingress/LoadBalancer: external access                             │ │
│  │  • HPA: horizontal pod autoscaler for backend                        │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Frontend (React + Nginx)

| Property | Value |
|----------|-------|
| Language | JavaScript (React) |
| Serving | Nginx in Docker container |
| Scaling | 1-2 replicas |
| Resources | 128Mi-256Mi memory, 100m-250m CPU |

**Responsibilities:**
- Browser-based game UI (lobby, game board, leaderboard)
- rApp catalogue display and deployment controls
- Basestation map view with event indicators
- WebSocket client for real-time updates
- Exponential backoff reconnection on connection loss

**Key Structure:**
```
frontend/
├── public/
├── src/
│   ├── components/
│   │   ├── Lobby/            # Create/join session, player list
│   │   ├── GameBoard/        # Map view with basestations
│   │   ├── Basestation/      # Individual basestation status
│   │   ├── RappCatalogue/    # Available rApps browser
│   │   ├── Leaderboard/      # Real-time score rankings
│   │   └── EventPanel/       # Active events display
│   ├── hooks/
│   │   ├── useWebSocket.js   # WebSocket connection management
│   │   └── useGameState.js   # Game state from WS messages
│   ├── services/
│   │   └── api.js            # REST API client
│   └── context/
│       └── GameContext.js    # Global game state
├── Dockerfile
└── package.json
```

### 2. Backend (Java Spring Boot)

| Property | Value |
|----------|-------|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Scaling | 1-3 replicas (HPA) |
| Resources | 512Mi-1Gi memory, 250m-500m CPU |

**Responsibilities:**
- Game logic and state management
- REST API for player actions and session management
- WebSocket server for real-time broadcasts
- Score calculation and leaderboard
- Game tick engine (scheduled metric updates)
- Server-side validation and access control
- Database persistence via Spring Data JPA

**Package Structure:**
```
backend/
├── src/main/java/com/rapptycoon/
│   ├── config/           # Spring config, WebSocket config
│   ├── controller/       # REST controllers
│   ├── websocket/        # WebSocket handlers, message types
│   ├── service/          # Business logic
│   ├── model/            # JPA entities
│   ├── repository/       # Spring Data JPA repositories
│   ├── dto/              # Data transfer objects
│   ├── security/         # Token validation, access control
│   ├── simulation/       # Game tick engine, metrics calc
│   └── factory/          # rApp creation (Factory pattern)
├── src/main/resources/
│   └── application.yml
├── Dockerfile
└── pom.xml
```

**Design Patterns:**
- **Strategy Pattern** — `RappBehaviour` interface; each rApp type defines its metric impact
- **Command Pattern** — Player actions (deploy, tune, disable, rollback) as command objects
- **Factory Pattern** — `RappFactory` creates rApp instances from catalogue templates
- **State Pattern** — rApp lifecycle transitions (DEPLOYING → ACTIVE → DISABLED → ROLLING_BACK)

### 3. Event Generator (Python)

| Property | Value |
|----------|-------|
| Language | Python 3.11+ |
| Scaling | 1 replica (singleton) |
| Resources | 256Mi-512Mi memory, 100m-250m CPU |

**Responsibilities:**
- Poll backend for active game sessions
- Generate events at configurable intervals (power outages, traffic spikes, hardware failures, SLA breaches)
- Push events to backend via internal REST API
- Configurable event probabilities and severity

**Structure:**
```
event-generator/
├── main.py           # Entry point, scheduling loop
├── events.py         # Event type definitions and generation
├── client.py         # Backend API client
├── config.py         # Intervals, probabilities, severity
├── requirements.txt
└── Dockerfile
```

### 4. MySQL Database

| Property | Value |
|----------|-------|
| Version | MySQL 8.0 |
| Deployment | StatefulSet (1 replica) |
| Resources | 1Gi-2Gi memory, 500m-1000m CPU |
| Storage | PersistentVolumeClaim |

**Tables:**
- `game_session` — Session state (LOBBY, ACTIVE, COMPLETED)
- `player` — Player info, scores, connection status
- `basestation` — Network nodes with metrics per player
- `rapp_template` — rApp catalogue definitions
- `rapp_deployment` — Active rApp instances on basestations
- `game_event` — Generated events with impact and resolution status

## Communication Patterns

```
┌──────────┐  REST (actions)   ┌──────────┐  REST (events)  ┌───────────┐
│ Frontend │──────────────────►│ Backend  │◄────────────────│  Event    │
│          │◄──────────────────│          │                 │ Generator │
│          │  WebSocket (live) │          │  REST (poll)    │           │
└──────────┘                   └────┬─────┘────────────────►└───────────┘
                                    │
                                    │ JDBC (Spring Data JPA)
                                    ▼
                               ┌──────────┐
                               │  MySQL   │
                               └──────────┘
```

| Path | Protocol | Purpose |
|------|----------|---------|
| Frontend → Backend | REST (HTTP) | Player actions, session management, catalogue |
| Backend → Frontend | WebSocket (STOMP) | Real-time game updates, events, leaderboard |
| Event Generator → Backend | REST (HTTP) | Push generated events |
| Event Generator ← Backend | REST (HTTP) | Poll active sessions |
| Backend → MySQL | JDBC | Persistence |

## API Overview

### Public REST Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/sessions` | Create new game session |
| POST | `/api/sessions/{code}/join` | Join session by code |
| POST | `/api/sessions/{code}/start` | Start game (host only) |
| GET | `/api/sessions/{code}` | Get session state |
| GET | `/api/sessions/{code}/basestations` | Get player's basestations |
| POST | `/api/sessions/{code}/rapps/deploy` | Deploy an rApp |
| PUT | `/api/sessions/{code}/rapps/{id}/tune` | Tune an rApp |
| PUT | `/api/sessions/{code}/rapps/{id}/disable` | Disable an rApp |
| PUT | `/api/sessions/{code}/rapps/{id}/rollback` | Roll back an rApp |
| GET | `/api/rapps/catalogue` | Get available rApps |

### Internal REST Endpoints (Event Generator only)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/internal/sessions/{code}/events` | Push new event |
| GET | `/api/internal/sessions/active` | Get active sessions |

### WebSocket Messages (STOMP over WebSocket)

| Direction | Type | Purpose |
|-----------|------|---------|
| Server → Client | `GAME_STARTED` | Game session started |
| Server → Client | `GAME_ENDED` | Game ended, final scores |
| Server → Client | `EVENT_OCCURRED` | New event on basestation |
| Server → Client | `METRICS_UPDATED` | Basestation metrics changed |
| Server → Client | `LEADERBOARD_UPDATED` | Score rankings changed |
| Server → Client | `RAPP_STATUS_CHANGED` | rApp status transition |
| Client → Server | `PLAYER_ACTION` | Player action (validated server-side) |

### Health Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/actuator/health/readiness` | Kubernetes readiness probe |
| GET | `/actuator/health/liveness` | Kubernetes liveness probe |

## Kubernetes Deployment

### Resource Summary

| Component | Replicas | Memory | CPU | Scaling |
|-----------|----------|--------|-----|---------|
| Backend | 1-3 | 512Mi-1Gi | 250m-500m | HPA (CPU-based) |
| Frontend | 1-2 | 128Mi-256Mi | 100m-250m | Manual |
| Event Generator | 1 | 256Mi-512Mi | 100m-250m | None (singleton) |
| MySQL | 1 | 1Gi-2Gi | 500m-1000m | None (StatefulSet) |

### Manifest Files

```
k8s/
├── backend-deployment.yaml
├── backend-service.yaml
├── backend-hpa.yaml
├── frontend-deployment.yaml
├── frontend-service.yaml
├── event-generator-deployment.yaml
├── mysql-statefulset.yaml
├── mysql-service.yaml
├── configmap.yaml
├── secret.yaml
└── ingress.yaml
```

### Scaling Strategy

- **Backend** scales horizontally via HPA to handle 100+ concurrent players
- WebSocket sessions are sticky (session affinity) to maintain connections during scaling
- **Frontend** serves static assets; scales easily with additional Nginx pods
- **Event Generator** runs as a singleton to avoid duplicate event generation
- **MySQL** is a single instance (sufficient for game workload; can add read replicas if needed)

## Security Model

- **Authentication**: Lightweight session-token-based (generated on join, validated on every request)
- **Authorisation**: Players can only access their own session; membership checked on all endpoints
- **Validation**: All inputs validated server-side (types, ranges, formats)
- **Score protection**: No client can directly modify scores or metrics
- **Error handling**: Error responses never expose internal implementation details
- **Secrets**: Stored in Kubernetes Secrets, never committed to source control

## Game Loop (Tick Engine)

```
Every tick (configurable interval, e.g., 5 seconds):
  1. For each active Game_Session:
     a. Apply active rApp impacts to basestation metrics
     b. Apply active event negative impacts to basestation metrics
     c. Escalate unresolved events (increase severity)
     d. Recalculate player scores
     e. Check game end condition
     f. Broadcast updates via WebSocket (metrics, leaderboard)
```

## Local Development

```bash
# Start all services locally
docker-compose up

# Services available at:
# Frontend:        http://localhost:3000
# Backend API:     http://localhost:8080
# MySQL:           localhost:3306
```

**Docker Compose** starts all four services together for local development without Kubernetes.

## Technology Stack Summary

| Layer | Technology |
|-------|-----------|
| Frontend | React, JavaScript, Nginx |
| Backend | Java 17+, Spring Boot 3.x, Spring Data JPA, Spring WebSocket |
| Event Generator | Python 3.11+, requests, schedule |
| Database | MySQL 8.0 |
| Containerisation | Docker |
| Orchestration | Kubernetes |
| Code Quality | SonarQube |
| Build (Backend) | Maven |
| Build (Frontend) | npm |
