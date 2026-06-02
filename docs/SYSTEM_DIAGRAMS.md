# rApp Tycoon — System Architecture & Gameplay Loop Diagrams

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              KUBERNETES CLUSTER                                   │
│                                                                                   │
│  ┌─────────────────────┐         ┌──────────────────────────┐                   │
│  │     FRONTEND         │         │         BACKEND           │                   │
│  │     (React)          │         │      (Spring Boot)        │                   │
│  │                      │  REST   │                          │                   │
│  │  • Lobby UI          │────────►│  Controllers:            │                   │
│  │  • Game Board        │         │   • GameSessionController│                   │
│  │  • Basestation Map   │◄────────│   • BasestationController│                   │
│  │  • rApp Catalogue    │ WebSocket│   • RappController       │                   │
│  │  • Leaderboard       │  (STOMP)│   • CatalogueController  │                   │
│  │  • Event Alerts      │         │   • LeaderboardController│                   │
│  │                      │         │   • InternalEventCtrl    │                   │
│  │  Pod(s): 1-2         │         │                          │                   │
│  └─────────────────────┘         │  Services:               │                   │
│                                   │   • GameSessionService   │                   │
│                                   │   • PlayerService        │                   │
│  ┌─────────────────────┐         │   • BasestationService   │                   │
│  │   EVENT GENERATOR    │  REST   │   • RappService          │                   │
│  │     (Python)         │────────►│   • EventService         │                   │
│  │                      │         │   • ScoreService         │                   │
│  │  • Polls active      │◄────────│                          │                   │
│  │    sessions          │  REST   │  Simulation:             │                   │
│  │  • Generates events  │         │   • GameTickEngine       │                   │
│  │    (power outages,   │         │     (@Scheduled 5s)      │                   │
│  │    traffic spikes,   │         │                          │                   │
│  │    hardware failures)│         │  WebSocket:              │                   │
│  │                      │         │   • WebSocketBroadcaster │                   │
│  │  Pod: 1 (singleton)  │         │   • WebSocketAuthInterceptor│                │
│  └─────────────────────┘         │   • GameActionController │                   │
│                                   │                          │                   │
│                                   │  Pod(s): 1-3 (HPA)      │                   │
│                                   └────────────┬─────────────┘                   │
│                                                │                                  │
│                                                │ JDBC (Spring Data JPA)           │
│                                                ▼                                  │
│                                   ┌──────────────────────────┐                   │
│                                   │         MySQL 8.4         │                   │
│                                   │                          │                   │
│                                   │  Tables:                 │                   │
│                                   │   • game_session         │                   │
│                                   │   • player               │                   │
│                                   │   • basestation          │                   │
│                                   │   • rapp_template        │                   │
│                                   │   • rapp_deployment      │                   │
│                                   │   • game_event           │                   │
│                                   │                          │                   │
│                                   │  StatefulSet: 1 replica  │                   │
│                                   └──────────────────────────┘                   │
│                                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────────────┐ │
│  │  ConfigMap: game.tick.interval=5000, game.tick.total=60, scoring weights    │ │
│  │  Secret: DB credentials, internal API key, session signing                  │ │
│  │  HPA: backend scales on CPU (1-3 replicas)                                  │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 2. Communication Flow

```
┌──────────┐                    ┌──────────┐                    ┌───────────┐
│ Frontend │                    │ Backend  │                    │  Event    │
│ (React)  │                    │ (Spring) │                    │ Generator │
└────┬─────┘                    └────┬─────┘                    └─────┬─────┘
     │                               │                                 │
     │  POST /api/sessions           │                                 │
     │──────────────────────────────►│                                 │
     │  201 {sessionCode, token}     │                                 │
     │◄──────────────────────────────│                                 │
     │                               │                                 │
     │  POST /api/sessions/X/join    │                                 │
     │──────────────────────────────►│                                 │
     │  200 {player, session}        │                                 │
     │◄──────────────────────────────│                                 │
     │                               │                                 │
     │  POST /api/sessions/X/start   │                                 │
     │──────────────────────────────►│  Assigns basestations           │
     │  200 {state: ACTIVE}          │  Starts tick engine             │
     │◄──────────────────────────────│                                 │
     │                               │                                 │
     │  WS CONNECT /ws/game          │                                 │
     │  (X-Session-Token header)     │                                 │
     │══════════════════════════════►│                                 │
     │  CONNECTED                    │                                 │
     │◄══════════════════════════════│                                 │
     │                               │                                 │
     │  SUBSCRIBE /topic/session/X/* │                                 │
     │══════════════════════════════►│                                 │
     │                               │                                 │
     │                               │  GET /api/internal/sessions/active
     │                               │◄────────────────────────────────│
     │                               │  200 [{sessionCode, basestationIds}]
     │                               │────────────────────────────────►│
     │                               │                                 │
     │                               │  POST /api/internal/sessions/X/events
     │                               │◄────────────────────────────────│
     │                               │  201 {eventId}                  │
     │                               │────────────────────────────────►│
     │                               │                                 │
     │  ◄─── TICK ENGINE (every 5s) ─┤                                 │
     │                               │                                 │
     │  WS: LEADERBOARD_UPDATED      │                                 │
     │◄══════════════════════════════│                                 │
     │  WS: METRICS_UPDATED          │                                 │
     │◄══════════════════════════════│                                 │
     │  WS: EVENT_OCCURRED           │                                 │
     │◄══════════════════════════════│                                 │
     │                               │                                 │
     │  POST /api/sessions/X/rapps/deploy                              │
     │──────────────────────────────►│                                 │
     │  201 {status: DEPLOYING}      │                                 │
     │◄──────────────────────────────│                                 │
     │                               │                                 │
     │  ◄─── NEXT TICK ─────────────┤                                 │
     │  WS: RAPP_STATUS_CHANGED      │  (DEPLOYING → ACTIVE)          │
     │◄══════════════════════════════│                                 │
     │                               │                                 │
```

## 3. Gameplay Loop (Tick Engine)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    GAME TICK ENGINE (every 5 seconds)                     │
│                                                                           │
│  For each ACTIVE game session:                                           │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 1: ACTIVATE DEPLOYING rApps                                     │ │
│  │                                                                       │ │
│  │  Find all rApps with status=DEPLOYING                                │ │
│  │  Set status → ACTIVE                                                  │ │
│  │  (They were deployed last tick, now ready to apply impact)           │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 2: APPLY rApp IMPACTS (per tick)                                │ │
│  │                                                                       │ │
│  │  For each basestation:                                                │ │
│  │    For each ACTIVE rApp on that basestation:                         │ │
│  │      Calculate impact using Strategy pattern (with aggressiveness)   │ │
│  │      Apply to basestation metrics (clamped 0-100)                    │ │
│  │                                                                       │ │
│  │  Example: Energy Saver (MODERATE) → +20 energy, -5 custExp, -30 cost│ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 2b: APPLY CONFLICT PENALTIES (per tick)                         │ │
│  │                                                                       │ │
│  │  For each basestation with 2+ active rApps:                          │ │
│  │    Check all pairs against conflict rules:                           │ │
│  │      • Energy Saver + Capacity Optimiser → -10 custExp, -5 energy   │ │
│  │      • Fault Predictor + Alarm Noise Reducer → -5 health, -8 autoRel│ │
│  │      • Traffic Balancer + Energy Saver → -7 energy, +15 cost        │ │
│  │    Apply penalty to basestation metrics                              │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 3: APPLY EVENT IMPACTS (per tick)                               │ │
│  │                                                                       │ │
│  │  For each unresolved event:                                          │ │
│  │    Combined multiplier = escalation_mult × severity_mult             │ │
│  │      Escalation: L0=×1, L1=×1.5, L2=×2, L3=×3                      │ │
│  │      Severity: LOW=×1, MED=×1.5, HIGH=×2, CRIT=×3                  │ │
│  │    Apply (base_impact × combined_multiplier) to basestation          │ │
│  │                                                                       │ │
│  │  Example: POWER_OUTAGE (HIGH, L1) → -15 health × (1.5 × 2.0) = -45│ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 4: ESCALATE EVENTS                                              │ │
│  │                                                                       │ │
│  │  For each unresolved event (skip tick 0):                            │ │
│  │    LOW severity → escalate every 3 ticks                             │ │
│  │    MEDIUM → every 2 ticks                                            │ │
│  │    HIGH/CRITICAL → every tick                                        │ │
│  │                                                                       │ │
│  │  If at max level (3): increment ticksAtMaxEscalation                 │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 5: CHECK EVENT RESOLUTION                                       │ │
│  │                                                                       │ │
│  │  For each basestation:                                                │ │
│  │    Check if any ACTIVE rApp is effective against unresolved events:  │ │
│  │      POWER_OUTAGE → Energy Saver or Fault Predictor                 │ │
│  │      TRAFFIC_SPIKE → Capacity Optimiser or Traffic Balancer          │ │
│  │      HARDWARE_FAILURE → Fault Predictor or Config Drift Detector    │ │
│  │      SLA_BREACH → SLA Guardian or Capacity Optimiser                │ │
│  │      INTERFERENCE → Traffic Balancer or Alarm Noise Reducer         │ │
│  │      CAPACITY_OVERFLOW → Capacity Optimiser or Traffic Balancer     │ │
│  │    If effective rApp found → resolve event                           │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 6: AUTO-RESOLVE                                                  │ │
│  │                                                                       │ │
│  │  For events at escalation level 3 with ticksAtMax >= 5:              │ │
│  │    Resolve the event                                                  │ │
│  │    Apply permanent -10 damage to ALL metrics on that basestation     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 7: RECALCULATE SCORES                                           │ │
│  │                                                                       │ │
│  │  For each player:                                                     │ │
│  │    satisfaction = avg(customerExperience) across basestations         │ │
│  │    stability = avg((health + autoRel + slaComp) / 3) across BSs      │ │
│  │    effectiveMoney = player.money - sum(basestation.cost)              │ │
│  │    compositeScore = money×0.30 + satisfaction×0.35 + stability×0.35  │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 8: BROADCAST VIA WEBSOCKET                                      │ │
│  │                                                                       │ │
│  │  → /topic/session/{code}/leaderboard: LEADERBOARD_UPDATED           │ │
│  │  → /topic/session/{code}/player/{id}/metrics: METRICS_UPDATED       │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ STEP 9: INCREMENT TICK + CHECK GAME END                              │ │
│  │                                                                       │ │
│  │  currentTick++                                                        │ │
│  │  If currentTick >= 60:                                                │ │
│  │    Transition session → COMPLETED                                    │ │
│  │    Broadcast GAME_ENDED with final leaderboard                       │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## 4. Game Session Lifecycle

```
                    ┌──────────────────┐
                    │   Player creates  │
                    │     session       │
                    └────────┬─────────┘
                             ▼
                    ┌──────────────────┐
                    │      LOBBY       │  ← Players join (2-6)
                    │                  │  ← Host can start when ≥2
                    └────────┬─────────┘
                             │ Host clicks START
                             ▼
                    ┌──────────────────┐
                    │     ACTIVE       │  ← Tick engine running
                    │                  │  ← Events generated
                    │  60 ticks        │  ← Players deploy/tune/disable rApps
                    │  (5 min)         │  ← Scores update each tick
                    └────────┬─────────┘
                             │ Tick 60 reached
                             ▼
                    ┌──────────────────┐
                    │    COMPLETED     │  ← Final scores shown
                    │                  │  ← Winner determined
                    │                  │  ← GAME_ENDED broadcast
                    └──────────────────┘
```

## 5. rApp Lifecycle

```
    Player deploys rApp (€50 deducted)
              │
              ▼
    ┌──────────────────┐
    │    DEPLOYING     │  ← No impact applied
    │    (1 tick)      │  ← Waiting for activation
    └────────┬─────────┘
             │ Next tick: activateDeployingRapps()
             ▼
    ┌──────────────────┐
    │     ACTIVE       │  ← Impact applied per tick
    │                  │  ← Can be tuned (version++)
    │                  │  ← Can resolve events
    │                  │  ← Conflict penalties if paired
    └───┬────┬────┬────┘
        │    │    │
        │    │    │ Player disables
        │    │    ▼
        │    │  ┌──────────────────┐
        │    │  │    DISABLED      │  ← Impact removed
        │    │  │                  │  ← Conflict penalties stop
        │    │  └──────────────────┘
        │    │
        │    │ Player tunes (threshold/aggressiveness)
        │    ▼
        │  ┌──────────────────┐
        │  │  ACTIVE (v2)     │  ← New multiplier applied
        │  │                  │  ← Previous config saved for rollback
        │  └──────────────────┘
        │
        │ Player rollback (version > 1 only)
        ▼
    ┌──────────────────┐
    │  ACTIVE (v1)     │  ← Reverts to previous config
    └──────────────────┘
```

## 6. Event Lifecycle

```
    Python generator pushes event
              │
              ▼
    ┌──────────────────┐
    │  UNRESOLVED (L0) │  ← Base impact applied per tick
    │  Severity: HIGH  │  ← Multiplier: 1.0 × 2.0 = ×2
    └────────┬─────────┘
             │ Escalates every tick (HIGH severity)
             ▼
    ┌──────────────────┐
    │  UNRESOLVED (L1) │  ← Impact × 1.5 × 2.0 = ×3
    └────────┬─────────┘
             │
             ▼
    ┌──────────────────┐
    │  UNRESOLVED (L2) │  ← Impact × 2.0 × 2.0 = ×4
    └────────┬─────────┘
             │
             ▼
    ┌──────────────────┐
    │  UNRESOLVED (L3) │  ← Impact × 3.0 × 2.0 = ×6 (MAX)
    │  ticksAtMax: 0   │  ← Counting ticks at max
    └───┬────┬─────────┘
        │    │
        │    │ Player deploys effective rApp
        │    ▼
        │  ┌──────────────────┐
        │  │    RESOLVED      │  ← Damage stops
        │  │                  │  ← Metrics don't recover automatically
        │  └──────────────────┘
        │
        │ ticksAtMax reaches 5 (no effective rApp deployed)
        ▼
    ┌──────────────────┐
    │  AUTO-RESOLVED   │  ← Permanent -10 to ALL metrics
    │  (with damage)   │  ← Player neglected too long
    └──────────────────┘
```


---

## 7. Mermaid Diagrams (Visual — renders in GitHub/GitLab/IDE)

### System Architecture (C4 Style)

```mermaid
graph TB
    subgraph Kubernetes Cluster
        FE[Frontend<br/>React + Nginx<br/>Pod 1-2]
        BE[Backend<br/>Spring Boot 3.5<br/>Pod 1-3 HPA]
        EG[Event Generator<br/>Python<br/>Pod 1]
        DB[(MySQL 8.4<br/>StatefulSet)]
        CM[ConfigMap<br/>Game Config]
        SEC[Secret<br/>DB Creds + API Key]
    end

    FE -->|REST API| BE
    BE -->|WebSocket STOMP| FE
    EG -->|REST: Push Events| BE
    BE -->|REST: Active Sessions| EG
    BE -->|JDBC| DB
    BE -.->|reads| CM
    BE -.->|reads| SEC

    style FE fill:#61dafb,color:#000
    style BE fill:#6db33f,color:#fff
    style EG fill:#3776ab,color:#fff
    style DB fill:#4479a1,color:#fff
```

### Game Session State Machine

```mermaid
stateDiagram-v2
    [*] --> LOBBY: Player creates session
    LOBBY --> LOBBY: Players join (2-6)
    LOBBY --> ACTIVE: Host starts (≥2 players)
    ACTIVE --> ACTIVE: Tick engine runs (every 5s)
    ACTIVE --> COMPLETED: Tick 60 reached
    COMPLETED --> [*]: Game over
```

### Tick Engine Flowchart

```mermaid
flowchart TD
    START([Every 5 seconds]) --> FETCH[Fetch ACTIVE sessions]
    FETCH --> LOOP{For each session}
    LOOP --> A[1. Activate DEPLOYING rApps]
    A --> B[2. Apply rApp impacts per tick]
    B --> C[2b. Apply conflict penalties]
    C --> D[3. Apply event impacts<br/>escalation × severity multiplier]
    D --> E[4. Escalate unresolved events]
    E --> F[5. Check event resolution<br/>effective rApp deployed?]
    F --> G[6. Auto-resolve events<br/>at max escalation 5+ ticks]
    G --> H[7. Recalculate all scores]
    H --> I[8. Broadcast via WebSocket<br/>leaderboard + metrics]
    I --> J[9. Increment tick counter]
    J --> K{tick >= 60?}
    K -->|Yes| END[End game → COMPLETED<br/>Broadcast GAME_ENDED]
    K -->|No| LOOP
    END --> DONE([Done])

    style START fill:#4caf50,color:#fff
    style END fill:#f44336,color:#fff
```

### rApp Lifecycle

```mermaid
stateDiagram-v2
    [*] --> DEPLOYING: Player deploys (€ deducted)
    DEPLOYING --> ACTIVE: Next tick activates
    ACTIVE --> ACTIVE: Tuned (version++)
    ACTIVE --> DISABLED: Player disables
    ACTIVE --> ACTIVE: Rolled back (version--)
    DISABLED --> [*]

    note right of DEPLOYING: No impact applied
    note right of ACTIVE: Impact applied per tick
    note right of DISABLED: Impact removed immediately
```

### Event Lifecycle

```mermaid
stateDiagram-v2
    [*] --> L0_UNRESOLVED: Event generated
    L0_UNRESOLVED --> L1_UNRESOLVED: Escalate
    L1_UNRESOLVED --> L2_UNRESOLVED: Escalate
    L2_UNRESOLVED --> L3_UNRESOLVED: Escalate (MAX)
    L3_UNRESOLVED --> AUTO_RESOLVED: 5 ticks at max<br/>-10 permanent damage

    L0_UNRESOLVED --> RESOLVED: Effective rApp deployed
    L1_UNRESOLVED --> RESOLVED: Effective rApp deployed
    L2_UNRESOLVED --> RESOLVED: Effective rApp deployed
    L3_UNRESOLVED --> RESOLVED: Effective rApp deployed

    RESOLVED --> [*]
    AUTO_RESOLVED --> [*]
```

### Score Calculation

```mermaid
flowchart LR
    M[Player Money<br/>€1000 - deploy costs] --> SUB[Subtract<br/>basestation costs]
    SUB --> EFF[Effective Money<br/>× 0.30]
    
    CE[Avg Customer<br/>Experience] --> SAT[Satisfaction<br/>× 0.35]
    
    H[Avg Health] --> STAB
    AR[Avg Auto<br/>Reliability] --> STAB
    SLA[Avg SLA<br/>Compliance] --> STAB
    STAB[Stability<br/>÷3 × 0.35]
    
    EFF --> SCORE[Composite<br/>Score]
    SAT --> SCORE
    STAB --> SCORE
```

### Communication Sequence

```mermaid
sequenceDiagram
    participant F as Frontend
    participant B as Backend
    participant E as Event Generator
    participant DB as MySQL

    F->>B: POST /api/sessions {hostName}
    B->>DB: Save session + player
    B-->>F: 201 {sessionCode, token}

    F->>B: POST /sessions/{code}/join
    B->>DB: Save player
    B-->>F: 200 {player, session}

    F->>B: POST /sessions/{code}/start
    B->>DB: State → ACTIVE, assign basestations
    B-->>F: 200 {state: ACTIVE}

    F->>B: WS CONNECT /ws/game
    B-->>F: CONNECTED

    loop Every 5 seconds (Tick Engine)
        E->>B: GET /internal/sessions/active
        B-->>E: [{sessionCode, basestationIds}]
        E->>B: POST /internal/sessions/{code}/events
        B->>DB: Save event
        B->>DB: Apply impacts, escalate, resolve
        B->>DB: Recalculate scores
        B-->>F: WS: LEADERBOARD_UPDATED
        B-->>F: WS: METRICS_UPDATED
    end

    F->>B: POST /sessions/{code}/rapps/deploy
    B->>DB: Deduct money, create deployment
    B-->>F: 201 {status: DEPLOYING}

    Note over B: Next tick activates rApp
    B-->>F: WS: RAPP_STATUS_CHANGED
```

---

## How to View These Diagrams

1. **GitHub/GitLab** — Push to repo, view the .md file — Mermaid renders automatically
2. **IntelliJ** — Install "Mermaid" plugin, then preview the markdown
3. **VS Code** — Install "Markdown Preview Mermaid Support" extension
4. **Online** — Paste into [mermaid.live](https://mermaid.live)
