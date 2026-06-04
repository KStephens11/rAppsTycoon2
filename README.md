# rApp Tycoon

A browser-based multiplayer strategy game where 2-6 players deploy and manage rApps to optimise a virtual 5G network. Built with React, Spring Boot, Python, and MySQL.

## Architecture

```
Frontend (React) ↔ Backend (Spring Boot) ↔ MySQL
                         ↑
               Event Generator (Python)
               Bot Player (Python)
```

The **Event Generator** continuously injects random events (power outages, faults, etc.) into active sessions via an internal API. The **Bot Player** is an AI-controlled player that uses a Python recommender module (`recommend.py`) to decide which rApps to deploy, tune, disable, or roll back.

## Prerequisites

- Docker Desktop (with Kubernetes enabled)
- kind (for local Kubernetes)
- Jenkins

## Local Development

**1. Clone and configure environment**
```bash
cp .env.example .env
```

The default `.env` values work out of the box for local development. No changes needed.

**2. Start all services**
```bash
docker compose up --build
```

Services start in order: MySQL → Backend → Event Generator → Frontend.

**3. Verify**
```bash
docker compose ps
```

All services should be running:
- `rapp-mysql` — `healthy`
- `rapp-backend` — `healthy`
- `rapp-event-generator` — `Up`
- `rapp-frontend` — `Up`

Backend health check:
```bash
curl http://localhost:8080/actuator/health
```

### Running a bot player locally

The bot player is an optional service gated behind a Docker Compose profile. It is normally spawned dynamically as a Kubernetes pod by the backend `BotManager`.

```bash
SESSION_CODE=ABCD1234 SESSION_TOKEN=<player-token> docker compose --profile bot run bot-player
```

| Variable | Description | Default |
|----------|-------------|---------|
| `SESSION_CODE` | Session to join | `TEST123` |
| `SESSION_TOKEN` | Bot player's session token | `changeme` |
| `DIFFICULTY` | Bot difficulty (`EASY`, `MEDIUM`, `HARD`) | `MEDIUM` |
| `BACKEND_BASE_URL` | Backend URL reachable from the container | `http://backend:8080` |
| `LOG_LEVEL` | Python log level | `INFO` |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password | — |
| `MYSQL_DATABASE` | Database name | `rapptycoon` |
| `MYSQL_USER` | Database user | `rapptycoon` |
| `MYSQL_PASSWORD` | Database password | — |
| `INTERNAL_API_KEY` | Shared secret between backend and event generator | — |

## Services

| Service | Port | Description |
|---------|------|-------------|
| Frontend | `3001` | React app served via Nginx (Docker) |
| Backend API | `8080` | Spring Boot REST API + WebSocket |
| MySQL | `3307` | Database (mapped from internal 3306) |
| Bot Player | `8081` | Optional AI player — `--profile bot` only |

## API

Base URL: `http://localhost:8080`

### Sessions

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/sessions` | Create a game session |
| GET | `/api/sessions/{code}` | Get session state |
| POST | `/api/sessions/{code}/join` | Join a session |
| POST | `/api/sessions/{code}/leave` | Leave a session |
| POST | `/api/sessions/{code}/start` | Start the game (host only, min 2 players) |
| POST | `/api/sessions/{code}/bots` | Spawn a bot player into the session |
| GET | `/api/sessions/{code}/leaderboard` | Get the current leaderboard |

### Basestations

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/sessions/{code}/basestations` | List all basestations and their state |

### rApps

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/rapps/catalogue` | List available rApp templates |
| POST | `/api/sessions/{code}/rapps/deploy` | Deploy a rApp to a basestation |
| PUT | `/api/sessions/{code}/rapps/{id}/tune` | Tune a deployed rApp |
| PUT | `/api/sessions/{code}/rapps/{id}/disable` | Disable a deployed rApp |
| PUT | `/api/sessions/{code}/rapps/{id}/rollback` | Roll back a deployed rApp to a previous version |

### AI Recommender

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/sessions/{code}/recommendations` | Get an AI-generated action recommendation for the requesting player |

### Other

| Method | Path | Description |
|--------|------|-------------|
| GET | `/actuator/health` | Health check |

## Kubernetes

Apply all manifests:
```bash
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/mysql-pvc.yaml
kubectl apply -f k8s/mysql-deployment.yaml
kubectl apply -f k8s/mysql-service.yaml
kubectl apply -f k8s/backend-deployment.yaml
kubectl apply -f k8s/backend-service.yaml
kubectl apply -f k8s/event-generator-deployment.yaml
kubectl apply -f k8s/frontend-deployment.yaml
kubectl apply -f k8s/frontend-service.yaml
kubectl apply -f k8s/frontend-hpa.yaml
```

Verify all pods are running:
```bash
kubectl get pods
kubectl get services
```

Access the frontend (NodePort does not work with kind, use port-forward):
```bash
kubectl port-forward service/frontend 8081:80
```

Then open `http://localhost:8081`.

## Jenkins

The `Jenkinsfile` at the root defines the CI/CD pipeline with the following stages:

| Stage | Description |
|-------|-------------|
| Checkout | Pulls latest code |
| Test | Runs backend unit tests and frontend tests |
| SonarQube | Static analysis via SonarCloud |
| Build Images | Builds Docker images for backend, event-generator, and frontend |
| Deploy | Deploys to Kubernetes on `main` branch |

Images built:
- `rapp-backend:${BUILD_NUMBER}`
- `rapp-event-generator:${BUILD_NUMBER}`
- `rapp-tycoon-frontend:${BUILD_NUMBER}`

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React, Nginx |
| Backend | Java 17, Spring Boot 3, Spring Data JPA, WebSocket |
| Event Generator | Python 3.11 |
| Bot Player / Recommender | Python 3.11 |
| Database | MySQL 8.4 |
| Containerisation | Docker |
| Orchestration | Kubernetes (see `k8s/`) |
| CI/CD | Jenkins + GitHub Actions + SonarCloud |
