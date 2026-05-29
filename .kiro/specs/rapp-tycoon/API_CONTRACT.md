# rApp Tycoon — API Contract

> This document defines the complete API contract between the Frontend and Backend teams.
> Both teams can work concurrently against these interfaces. The Backend team implements the endpoints;
> the Frontend team codes against the request/response shapes defined here.

**Base URL:** `http://localhost:8080` (local dev) or via Kubernetes Ingress in production.

**Authentication:** All endpoints (except session creation) require a `X-Session-Token` header containing the player's session token received on join.

**Content-Type:** All request and response bodies are `application/json`.

**Error Response Format (all endpoints):**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Human-readable description of the error",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Error Codes:**
| Code | HTTP Status | Meaning |
|------|-------------|---------|
| `VALIDATION_ERROR` | 400 | Invalid input data |
| `SESSION_NOT_FOUND` | 404 | Game session does not exist |
| `SESSION_FULL` | 409 | Lobby is at max capacity (6 players) |
| `UNAUTHORIZED` | 401 | Missing or invalid session token |
| `FORBIDDEN` | 403 | Player not a member of this session |
| `INVALID_STATE` | 409 | Action not allowed in current game state |
| `RAPP_NOT_FOUND` | 404 | rApp deployment does not exist |
| `BASESTATION_NOT_FOUND` | 404 | Basestation does not exist |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## 1. Session Management

### 1.1 Create Game Session

**`POST /api/sessions`**

Creates a new game session and returns the session code and host player token.

**Request Body:**
```json
{
  "hostName": "string (required, 1-50 chars)"
}
```

**Response: `201 Created`**
```json
{
  "sessionCode": "ABC12345",
  "sessionId": 1,
  "hostPlayer": {
    "id": 1,
    "displayName": "PlayerOne",
    "sessionToken": "a1b2c3d4e5f6..."
  },
  "state": "LOBBY",
  "maxPlayers": 6,
  "createdAt": "2025-01-15T10:00:00Z"
}
```

**Errors:** `VALIDATION_ERROR` (missing/invalid hostName)

---

### 1.2 Join Game Session

**`POST /api/sessions/{code}/join`**

Joins an existing game session lobby.

**Path Parameters:**
- `code` — 8-character session code

**Request Body:**
```json
{
  "displayName": "string (required, 1-50 chars)"
}
```

**Response: `200 OK`**
```json
{
  "player": {
    "id": 2,
    "displayName": "PlayerTwo",
    "sessionToken": "x9y8z7w6v5u4..."
  },
  "session": {
    "sessionCode": "ABC12345",
    "state": "LOBBY",
    "players": [
      { "id": 1, "displayName": "PlayerOne", "isHost": true },
      { "id": 2, "displayName": "PlayerTwo", "isHost": false }
    ],
    "maxPlayers": 6
  }
}
```

**Errors:** `SESSION_NOT_FOUND`, `SESSION_FULL`, `INVALID_STATE` (game already started), `VALIDATION_ERROR`

---

### 1.3 Start Game Session

**`POST /api/sessions/{code}/start`**

Starts the game. Only the host player can call this. Requires at least 2 players in the lobby.

**Path Parameters:**
- `code` — 8-character session code

**Headers:** `X-Session-Token: <host-player-token>`

**Request Body:** None

**Response: `200 OK`**
```json
{
  "sessionCode": "ABC12345",
  "state": "ACTIVE",
  "startedAt": "2025-01-15T10:05:00Z",
  "players": [
    {
      "id": 1,
      "displayName": "PlayerOne",
      "basestations": [
        { "id": 1, "name": "BS-Alpha", "positionX": 100, "positionY": 200 },
        { "id": 2, "name": "BS-Beta", "positionX": 300, "positionY": 150 },
        { "id": 3, "name": "BS-Gamma", "positionX": 200, "positionY": 350 }
      ]
    },
    {
      "id": 2,
      "displayName": "PlayerTwo",
      "basestations": [
        { "id": 4, "name": "BS-Delta", "positionX": 500, "positionY": 200 },
        { "id": 5, "name": "BS-Epsilon", "positionX": 700, "positionY": 150 },
        { "id": 6, "name": "BS-Zeta", "positionX": 600, "positionY": 350 }
      ]
    }
  ]
}
```

**Errors:** `FORBIDDEN` (not host), `INVALID_STATE` (not in LOBBY state or fewer than 2 players), `SESSION_NOT_FOUND`

---

### 1.4 Get Session State

**`GET /api/sessions/{code}`**

Returns the current state of a game session.

**Path Parameters:**
- `code` — 8-character session code

**Headers:** `X-Session-Token: <player-token>`

**Response: `200 OK`**
```json
{
  "sessionCode": "ABC12345",
  "state": "ACTIVE",
  "maxPlayers": 6,
  "createdAt": "2025-01-15T10:00:00Z",
  "startedAt": "2025-01-15T10:05:00Z",
  "endedAt": null,
  "players": [
    { "id": 1, "displayName": "PlayerOne", "isHost": true, "connected": true },
    { "id": 2, "displayName": "PlayerTwo", "isHost": false, "connected": true }
  ]
}
```

**Errors:** `SESSION_NOT_FOUND`, `FORBIDDEN`

---

## 2. Player Basestations

### 2.1 Get Player's Basestations

**`GET /api/sessions/{code}/basestations`**

Returns all basestations belonging to the authenticated player with current metrics.

**Path Parameters:**
- `code` — 8-character session code

**Headers:** `X-Session-Token: <player-token>`

**Response: `200 OK`**
```json
{
  "basestations": [
    {
      "id": 1,
      "name": "BS-Alpha",
      "positionX": 100,
      "positionY": 200,
      "metrics": {
        "health": 85.50,
        "customerExperience": 92.00,
        "cost": 150.00,
        "energyEfficiency": 78.00,
        "automationReliability": 95.00,
        "slaCompliance": 88.50
      },
      "deployedRapps": [
        {
          "id": 1,
          "templateId": 3,
          "name": "Fault Predictor",
          "status": "ACTIVE",
          "version": 1,
          "deployedAt": "2025-01-15T10:06:00Z"
        }
      ],
      "activeEvents": [
        {
          "id": 5,
          "eventType": "POWER_OUTAGE",
          "severity": "HIGH",
          "description": "Power supply failure detected at BS-Alpha",
          "escalationLevel": 2,
          "createdAt": "2025-01-15T10:07:00Z"
        }
      ]
    },
    {
      "id": 2,
      "name": "BS-Beta",
      "positionX": 300,
      "positionY": 150,
      "metrics": {
        "health": 100.00,
        "customerExperience": 100.00,
        "cost": 0.00,
        "energyEfficiency": 100.00,
        "automationReliability": 100.00,
        "slaCompliance": 100.00
      },
      "deployedRapps": [],
      "activeEvents": []
    }
  ]
}
```

**Errors:** `SESSION_NOT_FOUND`, `FORBIDDEN`, `INVALID_STATE` (game not started)

---

## 3. rApp Catalogue

### 3.1 Get rApp Catalogue

**`GET /api/rapps/catalogue`**

Returns all available rApps that players can deploy.

**Headers:** `X-Session-Token: <player-token>`

**Response: `200 OK`**
```json
{
  "rapps": [
    {
      "id": 1,
      "name": "Energy Saver",
      "purpose": "Reduces power consumption across basestation cells",
      "cost": 50.00,
      "benefit": "Lowers energy costs by optimising power usage during low-traffic periods",
      "risk": 25.00,
      "confidence": 80.00,
      "sideEffects": "May increase latency in high-load cells",
      "impact": {
        "health": 0.00,
        "customerExperience": -5.00,
        "cost": -30.00,
        "energyEfficiency": 20.00,
        "automationReliability": 0.00,
        "slaCompliance": -3.00
      }
    },
    {
      "id": 2,
      "name": "Capacity Optimiser",
      "purpose": "Dynamically allocates capacity based on demand patterns",
      "cost": 75.00,
      "benefit": "Improves customer experience by reducing congestion",
      "risk": 15.00,
      "confidence": 85.00,
      "sideEffects": "Higher operational cost during peak hours",
      "impact": {
        "health": 5.00,
        "customerExperience": 15.00,
        "cost": 20.00,
        "energyEfficiency": -5.00,
        "automationReliability": 5.00,
        "slaCompliance": 10.00
      }
    },
    {
      "id": 3,
      "name": "Fault Predictor",
      "purpose": "Predicts hardware failures before they occur",
      "cost": 60.00,
      "benefit": "Reduces downtime by enabling proactive maintenance",
      "risk": 20.00,
      "confidence": 70.00,
      "sideEffects": "False positives may trigger unnecessary maintenance",
      "impact": {
        "health": 15.00,
        "customerExperience": 5.00,
        "cost": 10.00,
        "energyEfficiency": 0.00,
        "automationReliability": 10.00,
        "slaCompliance": 8.00
      }
    },
    {
      "id": 4,
      "name": "SLA Guardian",
      "purpose": "Monitors and enforces SLA compliance thresholds",
      "cost": 45.00,
      "benefit": "Prevents SLA breaches by auto-adjusting network parameters",
      "risk": 10.00,
      "confidence": 90.00,
      "sideEffects": "May over-prioritise SLA metrics at expense of cost",
      "impact": {
        "health": 5.00,
        "customerExperience": 10.00,
        "cost": 15.00,
        "energyEfficiency": -2.00,
        "automationReliability": 5.00,
        "slaCompliance": 20.00
      }
    },
    {
      "id": 5,
      "name": "Configuration Drift Detector",
      "purpose": "Detects when basestation config deviates from baseline",
      "cost": 35.00,
      "benefit": "Maintains consistency and prevents silent degradation",
      "risk": 5.00,
      "confidence": 95.00,
      "sideEffects": "Alert fatigue if thresholds are too sensitive",
      "impact": {
        "health": 10.00,
        "customerExperience": 3.00,
        "cost": 5.00,
        "energyEfficiency": 2.00,
        "automationReliability": 15.00,
        "slaCompliance": 5.00
      }
    },
    {
      "id": 6,
      "name": "Traffic Balancer",
      "purpose": "Distributes traffic load across cells to prevent congestion",
      "cost": 65.00,
      "benefit": "Improves overall network throughput and user experience",
      "risk": 20.00,
      "confidence": 75.00,
      "sideEffects": "May cause brief handover interruptions during rebalancing",
      "impact": {
        "health": 8.00,
        "customerExperience": 12.00,
        "cost": 10.00,
        "energyEfficiency": -3.00,
        "automationReliability": 5.00,
        "slaCompliance": 7.00
      }
    },
    {
      "id": 7,
      "name": "Alarm Noise Reducer",
      "purpose": "Filters and correlates alarms to reduce noise",
      "cost": 30.00,
      "benefit": "Reduces operator fatigue and highlights real issues",
      "risk": 15.00,
      "confidence": 80.00,
      "sideEffects": "May suppress genuine alarms if correlation rules are too aggressive",
      "impact": {
        "health": 5.00,
        "customerExperience": 2.00,
        "cost": -5.00,
        "energyEfficiency": 0.00,
        "automationReliability": 12.00,
        "slaCompliance": 3.00
      }
    }
  ]
}
```

**Errors:** `UNAUTHORIZED`

---

## 4. rApp Deployment Actions

### 4.1 Deploy rApp

**`POST /api/sessions/{code}/rapps/deploy`**

Deploys an rApp from the catalogue to a specific basestation.

**Path Parameters:**
- `code` — 8-character session code

**Headers:** `X-Session-Token: <player-token>`

**Request Body:**
```json
{
  "templateId": 1,
  "basestationId": 1
}
```

**Response: `201 Created`**
```json
{
  "deployment": {
    "id": 10,
    "templateId": 1,
    "name": "Energy Saver",
    "basestationId": 1,
    "status": "DEPLOYING",
    "version": 1,
    "configuration": {},
    "deployedAt": "2025-01-15T10:08:00Z"
  }
}
```

**Errors:** `VALIDATION_ERROR`, `FORBIDDEN` (basestation not owned by player), `RAPP_NOT_FOUND` (invalid templateId), `BASESTATION_NOT_FOUND`, `INVALID_STATE` (game not active)

---

### 4.2 Tune rApp

**`PUT /api/sessions/{code}/rapps/{id}/tune`**

Adjusts the configuration of a deployed rApp to modify its behaviour.

**Path Parameters:**
- `code` — 8-character session code
- `id` — rApp deployment ID

**Headers:** `X-Session-Token: <player-token>`

**Request Body:**
```json
{
  "configuration": {
    "threshold": 75,
    "aggressiveness": "MODERATE"
  }
}
```

**Response: `200 OK`**
```json
{
  "deployment": {
    "id": 10,
    "templateId": 1,
    "name": "Energy Saver",
    "basestationId": 1,
    "status": "ACTIVE",
    "version": 2,
    "configuration": {
      "threshold": 75,
      "aggressiveness": "MODERATE"
    },
    "deployedAt": "2025-01-15T10:08:00Z"
  },
  "updatedMetrics": {
    "health": 87.00,
    "customerExperience": 90.00,
    "cost": 140.00,
    "energyEfficiency": 82.00,
    "automationReliability": 95.00,
    "slaCompliance": 89.00
  }
}
```

**Errors:** `RAPP_NOT_FOUND`, `FORBIDDEN`, `VALIDATION_ERROR`, `INVALID_STATE` (rApp not ACTIVE)

---

### 4.3 Disable rApp

**`PUT /api/sessions/{code}/rapps/{id}/disable`**

Disables a deployed rApp, removing its impact from the basestation.

**Path Parameters:**
- `code` — 8-character session code
- `id` — rApp deployment ID

**Headers:** `X-Session-Token: <player-token>`

**Request Body:** None

**Response: `200 OK`**
```json
{
  "deployment": {
    "id": 10,
    "templateId": 1,
    "name": "Energy Saver",
    "basestationId": 1,
    "status": "DISABLED",
    "version": 2,
    "configuration": {
      "threshold": 75,
      "aggressiveness": "MODERATE"
    },
    "deployedAt": "2025-01-15T10:08:00Z"
  },
  "updatedMetrics": {
    "health": 85.50,
    "customerExperience": 92.00,
    "cost": 150.00,
    "energyEfficiency": 78.00,
    "automationReliability": 95.00,
    "slaCompliance": 88.50
  }
}
```

**Errors:** `RAPP_NOT_FOUND`, `FORBIDDEN`, `INVALID_STATE` (rApp already disabled)

---

### 4.4 Rollback rApp

**`PUT /api/sessions/{code}/rapps/{id}/rollback`**

Rolls back an rApp to its previous version and configuration.

**Path Parameters:**
- `code` — 8-character session code
- `id` — rApp deployment ID

**Headers:** `X-Session-Token: <player-token>`

**Request Body:** None

**Response: `200 OK`**
```json
{
  "deployment": {
    "id": 10,
    "templateId": 1,
    "name": "Energy Saver",
    "basestationId": 1,
    "status": "ACTIVE",
    "version": 1,
    "configuration": {},
    "deployedAt": "2025-01-15T10:08:00Z"
  },
  "updatedMetrics": {
    "health": 85.50,
    "customerExperience": 87.00,
    "cost": 120.00,
    "energyEfficiency": 98.00,
    "automationReliability": 95.00,
    "slaCompliance": 85.50
  }
}
```

**Errors:** `RAPP_NOT_FOUND`, `FORBIDDEN`, `INVALID_STATE` (already at version 1, cannot rollback)

---

## 5. Leaderboard

### 5.1 Get Leaderboard

**`GET /api/sessions/{code}/leaderboard`**

Returns the current leaderboard for the session.

**Path Parameters:**
- `code` — 8-character session code

**Headers:** `X-Session-Token: <player-token>`

**Response: `200 OK`**
```json
{
  "leaderboard": [
    {
      "rank": 1,
      "playerId": 2,
      "displayName": "PlayerTwo",
      "scores": {
        "money": 850.00,
        "customerSatisfaction": 91.50,
        "networkStability": 88.00
      },
      "compositeScore": 276.50
    },
    {
      "rank": 2,
      "playerId": 1,
      "displayName": "PlayerOne",
      "scores": {
        "money": 720.00,
        "customerSatisfaction": 85.00,
        "networkStability": 82.50
      },
      "compositeScore": 245.80
    }
  ],
  "gameState": "ACTIVE"
}
```

**Errors:** `SESSION_NOT_FOUND`, `FORBIDDEN`

---

## 6. WebSocket Contract

### 6.1 Connection

**Endpoint:** `ws://localhost:8080/ws/game`

**Protocol:** STOMP over WebSocket

**Connection Headers:**
```
X-Session-Token: <player-token>
```

**Subscribe Destinations:**
| Destination | Purpose |
|-------------|---------|
| `/topic/session/{code}/game` | Game-wide broadcasts (start, end) |
| `/topic/session/{code}/leaderboard` | Leaderboard updates |
| `/topic/session/{code}/player/{playerId}/events` | Events targeting this player |
| `/topic/session/{code}/player/{playerId}/metrics` | Metric updates for this player |
| `/topic/session/{code}/player/{playerId}/rapps` | rApp status changes for this player |

**Send Destination:**
| Destination | Purpose |
|-------------|---------|
| `/app/session/{code}/action` | Player actions (validated server-side) |

---

### 6.2 Server → Client Messages

#### GAME_STARTED

Broadcast to all players when the host starts the game.

```json
{
  "type": "GAME_STARTED",
  "timestamp": "2025-01-15T10:05:00Z",
  "payload": {
    "sessionCode": "ABC12345",
    "players": [
      { "id": 1, "displayName": "PlayerOne" },
      { "id": 2, "displayName": "PlayerTwo" }
    ]
  }
}
```

#### GAME_ENDED

Broadcast to all players when the game ends.

```json
{
  "type": "GAME_ENDED",
  "timestamp": "2025-01-15T11:05:00Z",
  "payload": {
    "sessionCode": "ABC12345",
    "winner": {
      "playerId": 2,
      "displayName": "PlayerTwo",
      "compositeScore": 312.50
    },
    "finalLeaderboard": [
      {
        "rank": 1,
        "playerId": 2,
        "displayName": "PlayerTwo",
        "compositeScore": 312.50,
        "scores": { "money": 920.00, "customerSatisfaction": 94.00, "networkStability": 91.00 }
      },
      {
        "rank": 2,
        "playerId": 1,
        "displayName": "PlayerOne",
        "compositeScore": 278.30,
        "scores": { "money": 780.00, "customerSatisfaction": 88.00, "networkStability": 85.00 }
      }
    ]
  }
}
```

#### EVENT_OCCURRED

Sent to the affected player when a new event hits their basestation.

```json
{
  "type": "EVENT_OCCURRED",
  "timestamp": "2025-01-15T10:10:00Z",
  "payload": {
    "eventId": 5,
    "basestationId": 1,
    "basestationName": "BS-Alpha",
    "eventType": "POWER_OUTAGE",
    "severity": "HIGH",
    "description": "Power supply failure detected at BS-Alpha. Deploy Energy Saver or Fault Predictor to mitigate.",
    "impact": {
      "health": -15.00,
      "customerExperience": -10.00,
      "cost": 25.00,
      "energyEfficiency": -20.00,
      "automationReliability": -5.00,
      "slaCompliance": -12.00
    }
  }
}
```

#### METRICS_UPDATED

Sent to the player when their basestation metrics change (each game tick).

```json
{
  "type": "METRICS_UPDATED",
  "timestamp": "2025-01-15T10:10:05Z",
  "payload": {
    "basestationId": 1,
    "basestationName": "BS-Alpha",
    "metrics": {
      "health": 70.50,
      "customerExperience": 82.00,
      "cost": 175.00,
      "energyEfficiency": 58.00,
      "automationReliability": 90.00,
      "slaCompliance": 76.50
    }
  }
}
```

#### LEADERBOARD_UPDATED

Broadcast to all players when any player's score changes.

```json
{
  "type": "LEADERBOARD_UPDATED",
  "timestamp": "2025-01-15T10:10:05Z",
  "payload": {
    "leaderboard": [
      {
        "rank": 1,
        "playerId": 2,
        "displayName": "PlayerTwo",
        "compositeScore": 276.50,
        "scores": { "money": 850.00, "customerSatisfaction": 91.50, "networkStability": 88.00 }
      },
      {
        "rank": 2,
        "playerId": 1,
        "displayName": "PlayerOne",
        "compositeScore": 245.80,
        "scores": { "money": 720.00, "customerSatisfaction": 85.00, "networkStability": 82.50 }
      }
    ]
  }
}
```

#### RAPP_STATUS_CHANGED

Sent to the player when one of their rApp deployments changes status.

```json
{
  "type": "RAPP_STATUS_CHANGED",
  "timestamp": "2025-01-15T10:08:30Z",
  "payload": {
    "deploymentId": 10,
    "basestationId": 1,
    "name": "Energy Saver",
    "previousStatus": "DEPLOYING",
    "newStatus": "ACTIVE",
    "version": 1
  }
}
```

---

### 6.3 Client → Server Messages

#### PLAYER_ACTION

Player sends an action via WebSocket (alternative to REST for real-time actions).

```json
{
  "type": "PLAYER_ACTION",
  "payload": {
    "action": "DEPLOY",
    "templateId": 1,
    "basestationId": 1
  }
}
```

Supported action types: `DEPLOY`, `TUNE`, `DISABLE`, `ROLLBACK`

**TUNE action payload:**
```json
{
  "type": "PLAYER_ACTION",
  "payload": {
    "action": "TUNE",
    "deploymentId": 10,
    "configuration": {
      "threshold": 75,
      "aggressiveness": "MODERATE"
    }
  }
}
```

**DISABLE action payload:**
```json
{
  "type": "PLAYER_ACTION",
  "payload": {
    "action": "DISABLE",
    "deploymentId": 10
  }
}
```

**ROLLBACK action payload:**
```json
{
  "type": "PLAYER_ACTION",
  "payload": {
    "action": "ROLLBACK",
    "deploymentId": 10
  }
}
```

**Server Error Response (via WebSocket):**
```json
{
  "type": "ACTION_ERROR",
  "timestamp": "2025-01-15T10:09:00Z",
  "payload": {
    "error": "VALIDATION_ERROR",
    "message": "Cannot rollback rApp at version 1"
  }
}
```

---

## 7. Internal API (Event Generator → Backend)

> These endpoints are for internal service-to-service communication only.
> They should NOT be exposed externally. Secured via internal network policy or API key.

### 7.1 Get Active Sessions

**`GET /api/internal/sessions/active`**

Returns all game sessions currently in ACTIVE state.

**Headers:** `X-Internal-Key: <configured-api-key>`

**Response: `200 OK`**
```json
{
  "sessions": [
    {
      "sessionCode": "ABC12345",
      "playerCount": 4,
      "basestationIds": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      "startedAt": "2025-01-15T10:05:00Z"
    }
  ]
}
```

---

### 7.2 Push Event

**`POST /api/internal/sessions/{code}/events`**

Pushes a generated event into an active game session.

**Path Parameters:**
- `code` — 8-character session code

**Headers:** `X-Internal-Key: <configured-api-key>`

**Request Body:**
```json
{
  "basestationId": 1,
  "eventType": "POWER_OUTAGE",
  "severity": "HIGH",
  "description": "Power supply failure detected at BS-Alpha",
  "impact": {
    "health": -15.00,
    "customerExperience": -10.00,
    "cost": 25.00,
    "energyEfficiency": -20.00,
    "automationReliability": -5.00,
    "slaCompliance": -12.00
  }
}
```

**Supported Event Types:**
| Event Type | Description |
|------------|-------------|
| `POWER_OUTAGE` | Power supply failure at a basestation |
| `TRAFFIC_SPIKE` | Sudden increase in network traffic |
| `HARDWARE_FAILURE` | Physical component malfunction |
| `SLA_BREACH` | SLA threshold violation detected |
| `INTERFERENCE` | Radio interference from external source |
| `CAPACITY_OVERFLOW` | Basestation capacity exceeded |

**Supported Severity Levels:** `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`

**Response: `201 Created`**
```json
{
  "eventId": 5,
  "sessionCode": "ABC12345",
  "basestationId": 1,
  "eventType": "POWER_OUTAGE",
  "severity": "HIGH",
  "createdAt": "2025-01-15T10:10:00Z"
}
```

**Errors:** `SESSION_NOT_FOUND`, `BASESTATION_NOT_FOUND`, `INVALID_STATE` (session not active), `UNAUTHORIZED` (invalid internal key)

---

## 8. Health Endpoints

### 8.1 Readiness Probe

**`GET /actuator/health/readiness`**

Returns whether the service is ready to accept traffic (database connected, etc.).

**Response: `200 OK`**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

**Response: `503 Service Unavailable`** (when not ready)
```json
{
  "status": "DOWN",
  "components": {
    "db": { "status": "DOWN" }
  }
}
```

### 8.2 Liveness Probe

**`GET /actuator/health/liveness`**

Returns whether the service is alive (not deadlocked or crashed).

**Response: `200 OK`**
```json
{
  "status": "UP"
}
```

---

## 9. Data Types Reference

### Enums

**Game Session State:**
```
LOBBY | ACTIVE | COMPLETED
```

**rApp Deployment Status:**
```
DEPLOYING | ACTIVE | DISABLED | ROLLING_BACK
```

**Event Severity:**
```
LOW | MEDIUM | HIGH | CRITICAL
```

**Event Type:**
```
POWER_OUTAGE | TRAFFIC_SPIKE | HARDWARE_FAILURE | SLA_BREACH | INTERFERENCE | CAPACITY_OVERFLOW
```

**Player Action (WebSocket):**
```
DEPLOY | TUNE | DISABLE | ROLLBACK
```

### Common Objects

**Metrics Object:**
```json
{
  "health": "decimal (0.00 - 100.00)",
  "customerExperience": "decimal (0.00 - 100.00)",
  "cost": "decimal (0.00 - unlimited, lower is better)",
  "energyEfficiency": "decimal (0.00 - 100.00)",
  "automationReliability": "decimal (0.00 - 100.00)",
  "slaCompliance": "decimal (0.00 - 100.00)"
}
```

**Impact Object:**
```json
{
  "health": "decimal (positive = improvement, negative = degradation)",
  "customerExperience": "decimal",
  "cost": "decimal (positive = more expensive, negative = savings)",
  "energyEfficiency": "decimal",
  "automationReliability": "decimal",
  "slaCompliance": "decimal"
}
```

**Score Object:**
```json
{
  "money": "decimal (remaining budget, higher is better)",
  "customerSatisfaction": "decimal (0.00 - 100.00)",
  "networkStability": "decimal (0.00 - 100.00)"
}
```

### Score Calculation

```
compositeScore = (money * 0.3) + (customerSatisfaction * 0.35) + (networkStability * 0.35)
```

Weights are configurable via ConfigMap but default to the above.

---

## 10. Frontend Mock Server Guide

The frontend team can mock all REST endpoints using the response shapes above. Recommended approach:

1. Create a `mocks/` directory with JSON response files matching each endpoint
2. Use a tool like `json-server` or MSW (Mock Service Worker) to intercept API calls
3. For WebSocket, use a simple Node.js script that emits the message shapes above on a timer

This allows the frontend team to build the full UI without waiting for the backend to be complete.
