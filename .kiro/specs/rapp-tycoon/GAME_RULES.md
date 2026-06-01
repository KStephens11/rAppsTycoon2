# rApp Tycoon — Game Rules

## Game Overview

rApp Tycoon is a competitive multiplayer strategy game where 2-6 players manage virtual 5G network regions. Players deploy automation applications (rApps) to their basestations to optimise network performance while reacting to dynamically generated events. The player with the best combination of money, customer satisfaction, and network stability at the end wins.

---

## Game Setup

### Lobby
- A host creates a game session and receives a session code
- 2-6 players join using the session code
- The host starts the game once at least 2 players are in the lobby

### Starting Conditions (per player)
| Resource | Starting Value |
|----------|---------------|
| Money | €1,000.00 |
| Customer Satisfaction | 100.00 |
| Network Stability | 100.00 |
| Basestations | 3 per player |

### Basestation Starting Metrics
| Metric | Starting Value |
|--------|---------------|
| Health | 100.00 |
| Customer Experience | 100.00 |
| Cost | 0.00 |
| Energy Efficiency | 100.00 |
| Automation Reliability | 100.00 |
| SLA Compliance | 100.00 |

---

## Gameplay Loop

The game runs in real-time with a configurable tick interval (default: 5 seconds).

### Each Tick:
1. **Events fire** — The Python event generator pushes new events to random basestations
2. **Impacts apply** — Active rApps and unresolved events apply their per-tick impacts to basestation metrics
3. **Escalation** — Unresolved events escalate in severity (impacts worsen over time)
4. **Scores recalculate** — Player scores update based on current metrics across all their basestations
5. **Leaderboard broadcasts** — All players see updated rankings

### Player Actions (available at any time during active game):
- **Deploy** an rApp to a basestation
- **Tune** a deployed rApp's configuration
- **Disable** a deployed rApp (removes its effects)
- **Rollback** a tuned rApp to its previous version

### Game Flow Diagram:
```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  LOBBY      │────►│  GAME ACTIVE │────►│  GAME COMPLETE  │
│  (waiting)  │     │  (ticking)   │     │  (final scores) │
└─────────────┘     └──────────────┘     └─────────────────┘
                          │
                    ┌─────┴─────┐
                    ▼           ▼
              Events Fire    Players React
              (automatic)    (deploy/tune/disable/rollback)
```

---

## Win Condition

The game ends after a configurable number of ticks (default: 60 ticks = 5 minutes at 5s/tick).

**Winner:** The player with the highest composite score at game end.

### Score Calculation

```
compositeScore = (money × 0.30) + (customerSatisfaction × 0.35) + (networkStability × 0.35)
```

Where:
- **Money** = Starting money (€1,000) minus total rApp deployment costs, minus event cost impacts
- **Customer Satisfaction** = Average `customerExperience` across all player's basestations
- **Network Stability** = Average of (`health` + `automationReliability` + `slaCompliance`) / 3 across all basestations

### Tiebreaker
If two players have the same composite score, the tiebreaker is:
1. Higher customer satisfaction
2. Higher network stability
3. More remaining money

---

## rApp Catalogue

### Available rApps

| # | Name | Cost | Risk | Confidence | Primary Benefit | Key Drawback |
|---|------|------|------|------------|-----------------|--------------|
| 1 | Energy Saver | €50 | 25% | 80% | +20 energy efficiency | -5 customer experience |
| 2 | Capacity Optimiser | €75 | 15% | 85% | +15 customer experience | +20 cost |
| 3 | Fault Predictor | €60 | 20% | 70% | +15 health | +10 cost |
| 4 | SLA Guardian | €45 | 10% | 90% | +20 SLA compliance | +15 cost |
| 5 | Config Drift Detector | €35 | 5% | 95% | +15 automation reliability | +5 cost |
| 6 | Traffic Balancer | €65 | 20% | 75% | +12 customer experience | -3 energy efficiency |
| 7 | Alarm Noise Reducer | €30 | 15% | 80% | +12 automation reliability | May suppress real alarms |

### rApp Impact Details (per tick while active)

| rApp | Health | Cust. Exp. | Cost | Energy Eff. | Auto. Rel. | SLA Comp. |
|------|--------|------------|------|-------------|------------|-----------|
| Energy Saver | 0 | -5 | -30 | +20 | 0 | -3 |
| Capacity Optimiser | +5 | +15 | +20 | -5 | +5 | +10 |
| Fault Predictor | +15 | +5 | +10 | 0 | +10 | +8 |
| SLA Guardian | +5 | +10 | +15 | -2 | +5 | +20 |
| Config Drift Detector | +10 | +3 | +5 | +2 | +15 | +5 |
| Traffic Balancer | +8 | +12 | +10 | -3 | +5 | +7 |
| Alarm Noise Reducer | +5 | +2 | -5 | 0 | +12 | +3 |

> **Note:** Impact values are applied as a percentage-point change per tick. Metrics are clamped between 0 and 100 (except cost which has no upper limit).

### rApp Deployment Rules
- A player can deploy multiple rApps to the same basestation
- Each rApp costs money immediately on deployment (deducted from player's money)
- rApps take 1 tick to transition from DEPLOYING → ACTIVE (no impact during deployment tick)
- Disabling an rApp is instant — impact is removed immediately
- Rolling back reverts to the previous version's configuration, recalculates impact and partially refunds the player
- A player cannot rollback an rApp that is at version 1

---

## Events

### Event Types

| Event Type | Description | Typical Severity | Primary Impact |
|------------|-------------|------------------|----------------|
| Power Outage | Power supply failure at basestation | HIGH | Energy efficiency, health |
| Traffic Spike | Sudden surge in network demand | MEDIUM | Customer experience, SLA |
| Hardware Failure | Physical component malfunction | CRITICAL | Health, automation reliability |
| SLA Breach | SLA threshold violation detected | HIGH | SLA compliance, customer experience |
| Interference | Radio interference from external source | LOW | Health, customer experience |
| Capacity Overflow | Basestation capacity exceeded | MEDIUM | Customer experience, SLA compliance |

### Event Impact by Severity

| Severity | Impact Multiplier | Escalation Rate |
|----------|-------------------|-----------------|
| LOW | ×1.0 | +1 level per 3 ticks |
| MEDIUM | ×1.5 | +1 level per 2 ticks |
| HIGH | ×2.0 | +1 level per tick |
| CRITICAL | ×3.0 | +1 level per tick |

### Event Escalation

Unresolved events get worse over time:
- **Level 0** (initial): Base impact applied
- **Level 1**: Impact × 1.5
- **Level 2**: Impact × 2.0
- **Level 3**: Impact × 3.0 (maximum escalation)

### Resolving Events

Events are resolved when a player deploys an appropriate rApp that counteracts the event's impact:

| Event Type | Effective rApps (resolve or mitigate) |
|------------|---------------------------------------|
| Power Outage | Energy Saver, Fault Predictor |
| Traffic Spike | Capacity Optimiser, Traffic Balancer |
| Hardware Failure | Fault Predictor, Config Drift Detector |
| SLA Breach | SLA Guardian, Capacity Optimiser |
| Interference | Traffic Balancer, Alarm Noise Reducer |
| Capacity Overflow | Capacity Optimiser, Traffic Balancer |

- Deploying an effective rApp on the affected basestation resolves the event after 1 tick
- Deploying a partially effective rApp reduces escalation by 1 level per tick but does not fully resolve
- Events that reach escalation level 3 and remain unresolved for 5 more ticks auto-resolve but leave permanent metric damage (-10 to affected metrics)

---

## Side Effects and rApp Interactions

### Conflicting rApps

Certain rApp combinations on the same basestation create negative side effects:

| Combination | Side Effect | Penalty |
|-------------|-------------|---------|
| Energy Saver + Capacity Optimiser | Resource contention — energy savings conflict with capacity demands | -10 customer experience, -5 energy efficiency |
| Fault Predictor + Alarm Noise Reducer | Alarm suppression — noise reducer may hide fault predictions | -8 automation reliability, -5 health |
| Traffic Balancer + Energy Saver | Handover conflicts — balancing traffic increases power usage | -7 energy efficiency, +15 cost |

### Side Effect Rules
- Side effects trigger immediately when the conflicting combination is detected
- Side effects persist as long as both conflicting rApps remain active on the same basestation
- Disabling either rApp in the conflict removes the side effect
- Side effects stack — having 3 conflicting pairs means 3 separate penalties apply

---

## Game Balancing

### Event Generation Rate

| Player Count | Events per Tick (across all basestations) | Rationale |
|--------------|-------------------------------------------|-----------|
| 2 players | 0.3 (≈1 event every 3 ticks) | Manageable for small games |
| 3 players | 0.5 (≈1 event every 2 ticks) | Moderate pressure |
| 4 players | 0.7 (≈2 events every 3 ticks) | Increasing challenge |
| 5 players | 0.9 (≈1 event per tick) | High pressure |
| 6 players | 1.2 (≈6 events every 5 ticks) | Maximum chaos |

Events are distributed randomly across all basestations in the session (any player can be hit).

### Difficulty Curve

The game gets progressively harder as it progresses:

| Game Phase | Ticks | Event Severity Distribution | Notes |
|------------|-------|----------------------------|-------|
| Early (0-20) | 1-20 | 60% LOW, 30% MEDIUM, 10% HIGH | Gentle start, learn mechanics |
| Mid (21-40) | 21-40 | 30% LOW, 40% MEDIUM, 20% HIGH, 10% CRITICAL | Ramp up pressure |
| Late (41-60) | 41-60 | 10% LOW, 30% MEDIUM, 35% HIGH, 25% CRITICAL | High stakes, decisive plays |

### Economy Balance

- Starting money: €1,000
- Average rApp cost: €51 (range: €30-€75)
- A player can afford ~13-33 rApp deployments in a full game
- With 3 basestations and events hitting regularly, players must be selective
- Deploying too many rApps drains money (hurts score)
- Deploying too few rApps lets events degrade metrics (hurts score)
- The sweet spot is strategic deployment — right rApp, right basestation, right time

### Metric Clamping
- All percentage metrics (health, customer experience, energy efficiency, automation reliability, SLA compliance) are clamped to [0.00, 100.00]
- Cost has no upper limit (can grow indefinitely from events and rApp costs)
- Metrics cannot go negative

### Recovery Mechanics
- Disabling an rApp immediately removes its impact (both positive and negative)
- Resolving an event stops its per-tick damage but does NOT restore already-lost metrics
- Metrics only recover through active rApp positive impacts over time
- This means early neglect has lasting consequences

---

## Tuning an rApp

When a player tunes an rApp, they adjust its configuration parameters:

### Tunable Parameters

| Parameter | Range | Effect |
|-----------|-------|--------|
| Threshold | 1-100 | Higher = more aggressive (more benefit, more risk) |
| Aggressiveness | LOW, MODERATE, HIGH | Multiplier on all impacts (0.5×, 1.0×, 1.5×) |

### Tuning Effects
- **LOW aggressiveness**: All impacts × 0.5 (safer but less effective)
- **MODERATE aggressiveness**: All impacts × 1.0 (default behaviour)
- **HIGH aggressiveness**: All impacts × 1.5 (more powerful but riskier)

### Tuning Rules
- Tuning creates a new version (version increments by 1)
- Tuning takes effect on the next tick
- Players can rollback to the previous version if tuning makes things worse
- Tuning does not cost additional money

---

## Strategic Considerations

### Key Decisions Players Face:
1. **Deploy early vs. save money** — rApps cost money but prevent metric decay
2. **Specialise vs. diversify** — Focus rApps on one basestation or spread across all three
3. **React vs. prevent** — Wait for events and react, or deploy preventatively
4. **Tune aggressively vs. play safe** — Higher aggressiveness means more reward but more risk
5. **Manage conflicts** — Avoid deploying conflicting rApps on the same basestation

### Winning Strategies:
- **Balanced approach**: Deploy 1-2 rApps per basestation, tune moderately, react quickly to events
- **Aggressive approach**: Deploy many rApps early, tune to HIGH, accept side effects for raw metric gains
- **Economic approach**: Deploy minimally, save money, only react to HIGH/CRITICAL events
- **Specialist approach**: Focus all resources on keeping one metric perfect (e.g., SLA compliance)

---

## Configurable Parameters

These values are stored in the Kubernetes ConfigMap and can be adjusted for game balancing:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `game.tick.interval` | 5000ms | Time between game ticks |
| `game.tick.total` | 60 | Total ticks before game ends |
| `game.players.min` | 2 | Minimum players to start |
| `game.players.max` | 6 | Maximum players per session |
| `game.basestations.perPlayer` | 3 | Basestations assigned to each player |
| `game.score.weight.money` | 0.30 | Score weight for money |
| `game.score.weight.satisfaction` | 0.35 | Score weight for customer satisfaction |
| `game.score.weight.stability` | 0.35 | Score weight for network stability |
| `game.events.baseRate` | 0.3 | Base events per tick (scales with player count) |
| `game.events.playerMultiplier` | 0.2 | Additional events per tick per player above 2 |
| `game.escalation.maxLevel` | 3 | Maximum event escalation level |
| `game.escalation.autoResolveAfter` | 5 | Ticks at max escalation before auto-resolve |
| `game.rapp.deploymentTicks` | 1 | Ticks for rApp to go from DEPLOYING to ACTIVE |
| `game.rapp.resolveTicks` | 1 | Ticks for effective rApp to resolve an event |

---

## Example Round

**Tick 12 — Event fires:**
> ⚠️ POWER_OUTAGE (HIGH) hits Player 1's BS-Alpha
> Impact per tick: health -15, energy efficiency -20, cost +25

**Player 1 reacts (before tick 13):**
> Deploys Energy Saver (€50) to BS-Alpha
> Status: DEPLOYING (no impact yet)

**Tick 13:**
> Energy Saver transitions to ACTIVE
> Event still active but Energy Saver is effective against Power Outage
> Event marked for resolution

**Tick 14:**
> Event resolved. Energy Saver continues providing +20 energy efficiency per tick.
> Player 1 spent €50 but prevented further metric decay.

**Meanwhile, Player 2:**
> Has no events. Deploys SLA Guardian preventatively to BS-Delta.
> Gains +20 SLA compliance per tick but pays €45 and +15 cost per tick.

---

## Summary of Key Numbers

| Aspect | Value |
|--------|-------|
| Players per game | 2-6 |
| Basestations per player | 3 |
| Game duration | 60 ticks (5 minutes default) |
| Starting money | €1,000 |
| rApp cost range | €30-€75 |
| Event types | 6 |
| rApp types | 7 |
| Severity levels | 4 (LOW, MEDIUM, HIGH, CRITICAL) |
| Max escalation | Level 3 |
| Score weights | Money 30%, Satisfaction 35%, Stability 35% |
