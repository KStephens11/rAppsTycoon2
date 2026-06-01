# Tasks — Simulator / Event Generator (Python)

## Overview

The Event Generator is a Python singleton service that polls the backend for active game sessions and pushes randomly generated network events to them. It runs as a single Kubernetes pod and is configured entirely via environment variables sourced from the Kubernetes ConfigMap.

---

## Task 1: Project Scaffold and Configuration

### 1.1 Create project structure

Create the following file layout under `event-generator/`:

```
event-generator/
├── main.py
├── events.py
├── client.py
├── config.py
├── tests/
│   ├── __init__.py
│   ├── test_events.py
│   ├── test_client.py
│   └── test_config.py
├── requirements.txt
├── Dockerfile
└── .env.example
```

### 1.2 Implement `config.py` — environment variable configuration

Read all configuration from environment variables with sensible defaults. Expose a single `Config` dataclass (or named tuple) consumed by the rest of the service.

| Env Var | Default | Description |
|---|---|---|
| `BACKEND_BASE_URL` | `http://backend:8080` | Backend REST base URL |
| `INTERNAL_API_KEY` | *(required, no default)* | `X-Internal-Key` header value |
| `GAME_TICK_INTERVAL_MS` | `5000` | Milliseconds between ticks (matches backend) |
| `GAME_TICK_TOTAL` | `60` | Total ticks per game (used for difficulty phase calc) |
| `GAME_EVENTS_BASE_RATE` | `0.3` | Base events per tick for 2-player game |
| `GAME_EVENTS_PLAYER_MULTIPLIER` | `0.2` | Additional events per tick per player above 2 |
| `GAME_ESCALATION_MAX_LEVEL` | `3` | Maximum escalation level (informational) |
| `LOG_LEVEL` | `INFO` | Python logging level |

Acceptance criteria:
- Missing `INTERNAL_API_KEY` raises a clear `ValueError` at startup
- All numeric values are parsed and validated (must be positive numbers)
- `Config` is importable as a singleton instance from `config.py`

### 1.3 Write unit tests for `config.py`

- Test that missing `INTERNAL_API_KEY` raises `ValueError`
- Test that invalid numeric values (e.g. negative tick interval) raise `ValueError`
- Test that all defaults are applied correctly when env vars are absent
- Test that valid env vars override defaults

---

## Task 2: Backend API Client

### 2.1 Implement `client.py` — REST API client

Implement a `BackendClient` class with two public methods:

```python
class BackendClient:
    def get_active_sessions(self) -> list[dict]:
        """GET /api/internal/sessions/active
        Returns list of session dicts:
        [{ "sessionCode": str, "playerCount": int,
           "basestationIds": list[int], "startedAt": str }]
        Raises BackendClientError on non-200 response or network failure.
        """

    def push_event(self, session_code: str, event: dict) -> dict:
        """POST /api/internal/sessions/{code}/events
        event dict shape matches API_CONTRACT section 7.2 request body.
        Returns the created event dict on 201.
        Raises BackendClientError on non-201 response or network failure.
        """
```

Implementation requirements:
- Set `X-Internal-Key` header on every request from `Config.internal_api_key`
- Set `Content-Type: application/json` on POST requests
- Use the `requests` library with a configurable timeout (default 5 seconds)
- On HTTP error responses, log the status code and response body, then raise `BackendClientError`
- On network/connection errors, log the exception and raise `BackendClientError`
- `BackendClientError` is a custom exception defined in `client.py`

### 2.2 Write unit tests for `client.py`

Use `unittest.mock` or `pytest-mock` to mock `requests` calls. Do not make real HTTP calls in tests.

- Test `get_active_sessions` returns parsed list on 200
- Test `get_active_sessions` raises `BackendClientError` on 401, 500
- Test `get_active_sessions` raises `BackendClientError` on `ConnectionError`
- Test `push_event` sends correct JSON body and headers on success (201)
- Test `push_event` raises `BackendClientError` on 404 (`SESSION_NOT_FOUND`)
- Test `push_event` raises `BackendClientError` on `Timeout`

---

## Task 3: Event Generation Logic

### 3.1 Define event type catalogue in `events.py`

Define the six event types as structured data (dataclass or dict). Each entry must include:

| Field | Type | Description |
|---|---|---|
| `event_type` | `str` | One of: `POWER_OUTAGE`, `TRAFFIC_SPIKE`, `HARDWARE_FAILURE`, `SLA_BREACH`, `INTERFERENCE`, `CAPACITY_OVERFLOW` |
| `typical_severity` | `str` | Default severity for this event type |
| `impact` | `dict` | Base impact values for all six metrics at severity LOW (see below) |
| `description_template` | `str` | Human-readable description with `{basestation_name}` placeholder |

Base impact values per event type (at LOW severity, before multiplier):

| Event Type | health | customerExperience | cost | energyEfficiency | automationReliability | slaCompliance |
|---|---|---|---|---|---|---|
| POWER_OUTAGE | -7.5 | -5.0 | 12.5 | -10.0 | -2.5 | -6.0 |
| TRAFFIC_SPIKE | -3.0 | -8.0 | 5.0 | -2.0 | -1.0 | -7.0 |
| HARDWARE_FAILURE | -10.0 | -4.0 | 8.0 | -3.0 | -8.0 | -4.0 |
| SLA_BREACH | -2.0 | -7.0 | 6.0 | -1.0 | -2.0 | -12.0 |
| INTERFERENCE | -4.0 | -5.0 | 3.0 | -1.5 | -1.0 | -3.0 |
| CAPACITY_OVERFLOW | -3.0 | -9.0 | 7.0 | -2.0 | -1.5 | -8.0 |

> Note: The API_CONTRACT section 7.2 shows HIGH severity values for POWER_OUTAGE as health -15, energyEfficiency -20, cost +25. These are the LOW values × the HIGH multiplier (×2.0) from GAME_RULES. Use LOW as the base and apply the severity multiplier at generation time.

### 3.2 Implement severity selection with difficulty curve in `events.py`

Implement `select_severity(tick_number: int, total_ticks: int) -> str` using the difficulty curve from GAME_RULES:

| Phase | Tick Range | LOW% | MEDIUM% | HIGH% | CRITICAL% |
|---|---|---|---|---|---|
| Early | 0–33% of total | 60 | 30 | 10 | 0 |
| Mid | 34–66% of total | 30 | 40 | 20 | 10 |
| Late | 67–100% of total | 10 | 30 | 35 | 25 |

The function uses `random.choices` with the appropriate weights for the current phase.

### 3.3 Implement impact scaling in `events.py`

Implement `scale_impact(base_impact: dict, severity: str) -> dict` applying the severity multiplier from GAME_RULES:

| Severity | Multiplier |
|---|---|
| LOW | 1.0 |
| MEDIUM | 1.5 |
| HIGH | 2.0 |
| CRITICAL | 3.0 |

All impact values are multiplied by the severity multiplier. Values are rounded to 2 decimal places.

### 3.4 Implement event rate calculation in `events.py`

Implement `calculate_event_count(player_count: int, base_rate: float, player_multiplier: float) -> int` that:

- Computes the expected events per tick: `base_rate + (player_count - 2) * player_multiplier`
- Uses `random.random()` to probabilistically round to an integer (e.g. expected rate 0.7 → 70% chance of 1 event, 30% chance of 0)
- Returns 0 if `player_count < 2`

### 3.5 Implement `generate_event` in `events.py`

Implement `generate_event(basestation_id: int, basestation_name: str, tick_number: int, total_ticks: int) -> dict` that:

1. Randomly selects an event type from the catalogue (uniform distribution)
2. Calls `select_severity(tick_number, total_ticks)` to get severity
3. Calls `scale_impact(base_impact, severity)` to get final impact values
4. Returns a dict matching the API_CONTRACT section 7.2 request body shape:

```python
{
    "basestationId": int,
    "eventType": str,
    "severity": str,
    "description": str,   # formatted from description_template
    "impact": {
        "health": float,
        "customerExperience": float,
        "cost": float,
        "energyEfficiency": float,
        "automationReliability": float,
        "slaCompliance": float
    }
}
```

### 3.6 Write unit tests for `events.py`

- Test `select_severity` returns only valid severity strings
- Test `select_severity` in early phase never returns `CRITICAL`
- Test `select_severity` distribution is approximately correct over 10,000 samples (within 5% tolerance)
- Test `scale_impact` applies correct multiplier for each severity level
- Test `scale_impact` rounds values to 2 decimal places
- Test `calculate_event_count` returns 0 for player_count < 2
- Test `calculate_event_count` returns non-negative integer
- Test `generate_event` returns a dict with all required keys
- Test `generate_event` impact values are all non-zero (events always have some impact)

---

## Task 4: Main Scheduling Loop

### 4.1 Implement `main.py` — scheduling loop

Implement the main entry point using the `schedule` library. The loop must:

1. On startup, validate config (fail fast if `INTERNAL_API_KEY` is missing)
2. Schedule a tick job to run every `Config.tick_interval_seconds` seconds
3. Each tick job execution:
   a. Call `client.get_active_sessions()` — on `BackendClientError`, log warning and skip this tick
   b. For each active session:
      - Determine `player_count` and `basestation_ids` from the session dict
      - Calculate `tick_number` for this session (track per session code in memory)
      - Call `calculate_event_count(player_count, ...)` to determine how many events to generate
      - For each event to generate, randomly select a `basestation_id` from the session's `basestationIds`
      - Call `generate_event(basestation_id, ...)` — note: basestation name is not available from the active sessions response; use `f"BS-{basestation_id}"` as a fallback name
      - Call `client.push_event(session_code, event)` — on `BackendClientError`, log warning and continue to next event
   c. Log a summary at DEBUG level: sessions processed, events pushed, errors
4. Run `schedule.run_pending()` in a `while True` loop with a short `time.sleep(0.1)` to avoid busy-waiting

Per-session tick counter behaviour:
- When a session first appears in `get_active_sessions`, initialise its tick counter to 0
- Increment the counter each tick
- When a session no longer appears in `get_active_sessions` (completed or not found), remove it from the counter dict

### 4.2 Add graceful shutdown

Handle `KeyboardInterrupt` and `SIGTERM` (via `signal` module) to exit cleanly with a log message. No in-flight events should be lost — complete the current tick before shutting down.

### 4.3 Add structured logging

Configure Python `logging` at startup using `Config.log_level`. All log messages must include:
- Timestamp
- Log level
- Module name
- Message

Use `logging.basicConfig` with format: `%(asctime)s [%(levelname)s] %(name)s: %(message)s`

---

## Task 5: Dockerfile

### 5.1 Write `Dockerfile`

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

CMD ["python", "main.py"]
```

Requirements:
- Use `python:3.11-slim` base image
- Do not run as root — add a non-root user (`appuser`) and switch to it before `CMD`
- No dev dependencies in the image

### 5.2 Write `requirements.txt`

Pin exact versions for all dependencies:

```
requests==2.31.0
schedule==1.2.1
pytest==7.4.3
pytest-mock==3.12.0
```

> Verify these are the latest stable versions at time of implementation and update if needed.

### 5.3 Write `.env.example`

Document all supported environment variables with example values:

```
BACKEND_BASE_URL=http://localhost:8080
INTERNAL_API_KEY=change-me-in-production
GAME_TICK_INTERVAL_MS=5000
GAME_TICK_TOTAL=60
GAME_EVENTS_BASE_RATE=0.3
GAME_EVENTS_PLAYER_MULTIPLIER=0.2
GAME_ESCALATION_MAX_LEVEL=3
LOG_LEVEL=INFO
```

---

## Task 6: Docker Compose Integration

### 6.1 Add `event-generator` service to `docker-compose.yml`

Add the event generator as a service in the root `docker-compose.yml` (create the file if it does not exist). The service must:

- Build from `./event-generator/Dockerfile`
- Depend on `backend` (use `depends_on` with `condition: service_healthy` if the backend has a healthcheck)
- Inject all config env vars from a `.env` file or inline defaults
- Set `BACKEND_BASE_URL=http://backend:8080` to use Docker Compose internal DNS
- Restart policy: `on-failure`

Example service block:

```yaml
event-generator:
  build: ./event-generator
  restart: on-failure
  depends_on:
    backend:
      condition: service_healthy
  environment:
    BACKEND_BASE_URL: http://backend:8080
    INTERNAL_API_KEY: ${INTERNAL_API_KEY:-dev-key}
    GAME_TICK_INTERVAL_MS: ${GAME_TICK_INTERVAL_MS:-5000}
    GAME_TICK_TOTAL: ${GAME_TICK_TOTAL:-60}
    GAME_EVENTS_BASE_RATE: ${GAME_EVENTS_BASE_RATE:-0.3}
    GAME_EVENTS_PLAYER_MULTIPLIER: ${GAME_EVENTS_PLAYER_MULTIPLIER:-0.2}
    LOG_LEVEL: ${LOG_LEVEL:-INFO}
```

---

## Task 7: Kubernetes Manifest

### 7.1 Write `k8s/event-generator-deployment.yaml`

Create a Kubernetes Deployment manifest for the event generator:

- `replicas: 1` (singleton — must never scale above 1)
- Container image: `rapptycoon/event-generator:latest` (placeholder tag)
- Resource requests: `memory: 256Mi`, `cpu: 100m`
- Resource limits: `memory: 512Mi`, `cpu: 250m`
- Inject all env vars from the `game-config` ConfigMap and `game-secrets` Secret:
  - `GAME_TICK_INTERVAL_MS`, `GAME_TICK_TOTAL`, `GAME_EVENTS_BASE_RATE`, `GAME_EVENTS_PLAYER_MULTIPLIER` from ConfigMap
  - `INTERNAL_API_KEY` from Secret
  - `BACKEND_BASE_URL` as a hardcoded value pointing to the backend Service DNS name
- Liveness probe: exec `python -c "import main"` (or a simple health file touch in the loop) — keep it simple
- No HPA — add a comment in the manifest explaining why scaling is intentionally disabled

---

## Task Dependency Order

```
Task 1 (scaffold + config)
  └── Task 2 (client)
        └── Task 3 (event logic)
              └── Task 4 (main loop)
                    ├── Task 5 (Dockerfile)
                    └── Task 6 (Docker Compose)
                          └── Task 7 (K8s manifest)
```

All unit tests (Tasks 1.3, 2.2, 3.6) should be written alongside their respective implementation tasks, not deferred.
