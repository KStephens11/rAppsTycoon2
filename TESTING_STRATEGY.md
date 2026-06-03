# rApp Tycoon - Backend Testing Strategy

## Overview

Three test layers for the backend. No MockMVC — controller behaviour is validated at the integration layer via Karate against the running server.

| Layer | Framework | Scope |
|-------|-----------|-------|
| Unit | JUnit 5 + Mockito | Services, factories, simulation logic |
| Integration | Karate | REST API contracts and controller tests against running server |
| E2E | Selenium (minimal) | Browser smoke paths only |

### Requirements Coverage Map

| Requirement | Layer | Location |
|-------------|-------|----------|
| Unit tests for rApp effects | Unit | `RappBehaviourTest`, `RappServiceTest`, `GameTickEngineTest` |
| Unit tests for scoring | Unit | `ScoreServiceTest` |
| Unit tests for rApp lifecycle | Unit | `RappServiceTest`, `RappFactoryTest` |
| API / controller tests | Integration | Karate feature files — all endpoints |
| Persistence integration tests | Unit (slice) | `@DataJpaTest` repository tests |
| Negative tests for invalid actions | Unit + Integration | Exception paths in service tests + Karate error scenarios |

---

## 1. Unit Testing - JUnit 5 + Mockito

### Philosophy

Pure business logic in isolation. No Spring context loaded. Use `@ExtendWith(MockitoExtension.class)` only. Repository tests use `@DataJpaTest` with H2.

---

### 1.1 rApp Effects Tests (`RappBehaviourTest`)

Tests that each rApp behaviour calculates the correct `MetricDeltas` at every `Aggressiveness` level. This is a critical correctness property: the game balance depends entirely on these numbers being right.

**All 7 rApps must be tested. For each:**

| Test | What it verifies |
|------|-----------------|
| `calculateImpact(LOW)` | Deltas are base values x LOW multiplier |
| `calculateImpact(MODERATE)` | Deltas are base values x MODERATE multiplier |
| `calculateImpact(HIGH)` | Deltas are base values x HIGH multiplier |
| `calculateImpact(AGGRESSIVE)` | Deltas are base values x AGGRESSIVE multiplier |
| Scale is 2 decimal places | All returned BigDecimals use HALF_UP, scale 2 |
| Correct metric signs | Beneficial metrics are positive, costs/penalties are negative |

**Example (Energy Saver at MODERATE, multiplier = 1.0x):**

```java
@Test
@DisplayName("Energy Saver MODERATE: energyEfficiency = +20.00, customerExperience = -5.00")
void energySaverModerateImpact() {
    EnergySaverBehaviour behaviour = new EnergySaverBehaviour();

    MetricDeltas deltas = behaviour.calculateImpact(Aggressiveness.MODERATE);

    assertThat(deltas.health()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(deltas.customerExperience()).isEqualByComparingTo(new BigDecimal("-5.00"));
    assertThat(deltas.cost()).isEqualByComparingTo(new BigDecimal("-30.00"));
    assertThat(deltas.energyEfficiency()).isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(deltas.automationReliability()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(deltas.slaCompliance()).isEqualByComparingTo(new BigDecimal("-3.00"));
}
```

**Also test conflict detection in `RappServiceTest`:**
- `Energy Saver + Capacity Optimiser` → penalty applied (customerExperience -10, energyEfficiency -5)
- Non-conflicting pair → `null` returned

---

### 1.2 Scoring Tests (`ScoreServiceTest`)

Tests the composite score formula and leaderboard ranking. The formula is:

```
compositeScore = (effectiveMoney × weightMoney)
              + (avgCustomerExperience × weightSatisfaction)
              + (avgNetworkStability × weightStability)

networkStability per basestation = (health + automationReliability + slaCompliance) / 3
effectiveMoney = scoreMoney - sum(basestationCost)  [floored at 0]
```

| Test | What it verifies |
|------|-----------------|
| Composite score formula | Correct weighted sum with default weights (0.30 / 0.35 / 0.35) |
| Customer satisfaction = avg customerExperience | Averaged across all player basestations |
| Network stability calculation | Per-BS average of health + automationReliability + slaCompliance, then averaged |
| Effective money deducts basestation costs | `scoreMoney - totalCost`, not raw `scoreMoney` |
| Effective money floors at zero | Negative net money becomes 0, not negative |
| No basestations → satisfaction and stability = 0 | Edge case: player with no basestations |
| `recalculateAllScores` updates every player in session | All players in session are recalculated |
| Leaderboard sorted by composite score descending | Highest composite score is rank 1 |
| Leaderboard tiebreaker: satisfaction | Equal composite → higher satisfaction wins |
| Leaderboard tiebreaker: stability | Equal composite + satisfaction → higher stability wins |
| Leaderboard tiebreaker: money | Equal on all above → higher money wins |
| `determineWinner` returns rank-1 player | Returns first entry from sorted leaderboard |
| `determineWinner` throws when no players | `EntityNotFoundException` for empty session |

```java
@Test
@DisplayName("composite score uses weighted sum: money*0.3 + satisfaction*0.35 + stability*0.35")
void compositeScoreFormula() {
    // effectiveMoney = 1000 - 0 cost = 1000
    // customerSatisfaction = 80.00
    // networkStability = (90+95+88)/3 = 91.00
    // composite = (1000*0.3) + (80*0.35) + (91*0.35) = 300 + 28 + 31.85 = 359.85
    ...
    assertThat(updated.getCompositeScore()).isEqualByComparingTo(new BigDecimal("359.85"));
}

@Test
@DisplayName("effective money floors at zero when costs exceed player money")
void effectiveMoneyFloorsAtZero() {
    // player.scoreMoney = 100, basestation.cost = 500 → effectiveMoney = 0
    ...
    assertThat(updated.getCompositeScore())
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
}
```

---

### 1.3 rApp Lifecycle Tests (`RappServiceTest` + `RappFactoryTest`)

Tests every valid and invalid transition in the rApp state machine:

```
DEPLOYING → ACTIVE   (activate, called by tick engine after 1 tick)
ACTIVE    → DISABLED (disable)
ACTIVE    → ACTIVE   (tune — same status, new version)
ACTIVE    → ACTIVE   (rollback — same status, previous version)
```

| Test | Class | What it verifies |
|------|-------|-----------------|
| `deploy` creates deployment with DEPLOYING status | `RappFactoryTest` | Initial status is DEPLOYING |
| `deploy` sets version = 1 | `RappFactoryTest` | First deployment starts at version 1 |
| `deploy` sets deployedAt timestamp | `RappFactoryTest` | Timestamp is non-null |
| `deploy` deducts template cost from player money | `RappServiceTest` | scoreMoney reduced by template.cost |
| `deploy` throws when player does not own basestation | `RappServiceTest` | `ForbiddenException` |
| `deploy` throws for unknown templateId | `RappServiceTest` | `EntityNotFoundException` |
| `activate` transitions DEPLOYING → ACTIVE | `RappServiceTest` | Status becomes ACTIVE |
| `activate` throws when not in DEPLOYING status | `RappServiceTest` | `InvalidStateException` |
| `disable` transitions ACTIVE → DISABLED | `RappServiceTest` | Status becomes DISABLED |
| `disable` reverses the rApp's metric impact on basestation | `RappServiceTest` | Negated MetricDeltas applied |
| `disable` throws when not ACTIVE | `RappServiceTest` | `InvalidStateException` |
| `disable` throws when player does not own deployment | `RappServiceTest` | `ForbiddenException` |
| `tune` increments version and updates configuration | `RappServiceTest` | version = oldVersion + 1 |
| `tune` stores previous configuration | `RappServiceTest` | previousConfiguration holds old JSON |
| `tune` applies aggressiveness multiplier difference to metrics | `RappServiceTest` | Correct delta applied |
| `tune` throws when not ACTIVE | `RappServiceTest` | `InvalidStateException` |
| `rollback` reverts to previous version and config | `RappServiceTest` | version decrements, config restored |
| `rollback` throws when version = 1 (no previous version) | `RappServiceTest` | `InvalidStateException` |
| `processTick` activates DEPLOYING rApps after one tick | `GameTickEngineTest` | Status flips to ACTIVE |
| `processTick` applies active rApp MetricDeltas each tick | `GameTickEngineTest` | `basestationService.updateMetrics` called with correct deltas |

---

### 1.4 Game Tick Engine Tests (`GameTickEngineTest`)

| Test | What it verifies |
|------|-----------------|
| Event impacts scaled by escalation and severity multipliers | Combined multiplier = escalationMultiplier × severityMultiplier |
| HIGH events escalate every tick | `escalateEvent` called every tick for HIGH severity |
| MEDIUM events escalate every 2 ticks | `escalateEvent` called on even ticks only |
| LOW events escalate every 3 ticks | `escalateEvent` called when `tick % 3 == 0` |
| Events resolved when effective rApp deployed | `resolveEvent` called when `checkEventResolution` returns events |
| Auto-resolve at max escalation level | `checkAutoResolve` called when `escalationLevel >= maxLevel` |
| Scores recalculated every tick | `scoreService.recalculateAllScores` called |
| Tick counter increments | `session.currentTick` increases by 1 |
| Game ends when tick reaches total (60) | `gameSessionService.endSession` called |
| Game does not end before tick total | `endSession` never called below tick 60 |
| Completed sessions skipped | `tick()` does not process sessions not in ACTIVE state |
| Escalation multipliers: 0→1.0x, 1→1.5x, 2→2.0x, 3→3.0x | `getEscalationMultiplier` returns correct values |
| Severity multipliers: LOW→1.0x, MEDIUM→1.5x, HIGH→2.0x, CRITICAL→3.0x | `getSeverityMultiplier` returns correct values |

---

### 1.5 Persistence Integration Tests (`@DataJpaTest`)

Uses `@DataJpaTest` + `@ActiveProfiles("test")` with H2 in-memory database. Tests custom repository query methods against a real (in-memory) SQL engine.

**Five repository test classes, each covering:**

| Repository | Tests |
|------------|-------|
| `GameSessionRepositoryTest` | save + findById; findBySessionCode; findByState; unique constraint on sessionCode |
| `PlayerRepositoryTest` | save + findById; findBySessionId; findBySessionToken; unique constraint on token |
| `BasestationRepositoryTest` | save + findById; findByPlayerId; multiple basestations per player |
| `RappDeploymentRepositoryTest` | findByBasestationIdAndStatus (DEPLOYING, ACTIVE); findByPlayerId |
| `GameEventRepositoryTest` | findByBasestationIdAndResolved; findBySessionId; severity and escalation fields persist correctly |

```java
@DataJpaTest
@ActiveProfiles("test")
class RappDeploymentRepositoryTest {

    @Autowired
    private RappDeploymentRepository rappDeploymentRepository;

    @Test
    @DisplayName("findByBasestationIdAndStatus returns only ACTIVE deployments")
    void findByBasestationIdAndStatus_activeOnly() {
        // persist one ACTIVE, one DEPLOYING deployment on the same basestation
        // assert only ACTIVE deployment returned when querying ACTIVE
    }
}
```

---

### 1.6 Negative / Invalid Action Tests

Negative tests are co-located with their positive counterparts in the same service test class.

**`GameSessionServiceTest` — invalid actions:**
- Join a session in ACTIVE state → `InvalidStateException`
- Join a session in COMPLETED state → `InvalidStateException`
- Join when lobby is full (6 players) → `SessionFullException`
- Join with non-existent code → `SessionNotFoundException`
- Start with invalid token → `UnauthorizedException`
- Start with non-host token → `ForbiddenException`
- Start when session is not in LOBBY → `InvalidStateException`
- Start with fewer than 2 players → `InvalidStateException`
- End a session not in ACTIVE state → `InvalidStateException`
- Get session with non-existent code → `SessionNotFoundException`

**`RappServiceTest` — invalid actions:**
- Deploy to basestation not owned by player → `ForbiddenException`
- Deploy with non-existent templateId → `EntityNotFoundException`
- Activate rApp not in DEPLOYING status → `InvalidStateException`
- Disable rApp not in ACTIVE status → `InvalidStateException`
- Disable rApp owned by another player → `ForbiddenException`
- Tune rApp not in ACTIVE status → `InvalidStateException`
- Rollback rApp at version 1 → `InvalidStateException`

**`GameTickEngineTest` — negative behaviour:**
- Non-ACTIVE sessions are not processed by `tick()`

---

### 1.7 Conventions

```java
@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private BasestationRepository basestationRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private GameProperties gameProperties;

    @InjectMocks
    private ScoreService scoreService;

    @Nested
    @DisplayName("calculateCompositeScore")
    class CalculateCompositeScore {

        @Test
        @DisplayName("composite score uses weighted sum of money, satisfaction, and stability")
        void compositeScoreFormula() { ... }
    }
}
```

- `@Nested` classes per method under test
- `@DisplayName` strings are plain sentences
- AssertJ for all assertions (`assertThat`, `isEqualByComparingTo` for BigDecimal)
- Repository tests: `@DataJpaTest` + `@ActiveProfiles("test")`

### Run

```bash
cd backend && ./mvnw test
```

---

## 2. API / Controller Tests - Karate

### Why Karate

Karate tests hit real HTTP endpoints on the running Spring Boot server. This validates the full controller pipeline (request parsing → service → exception handler → JSON serialisation) with nothing mocked. No MockMVC.

### Dependency

```xml
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-junit5</artifactId>
    <version>1.4.1</version>
    <scope>test</scope>
</dependency>
```

Add `maven-failsafe-plugin` to run Karate in the `verify` phase:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes><include>**/KarateRunner.java</include></includes>
    </configuration>
</plugin>
```

### Directory Layout

```
src/test/
  java/com/rapptycoon/integration/
    KarateRunner.java
    karate-config.js
  resources/karate/
    sessions/
      create-session.feature
      join-session.feature
      start-session.feature
      get-session.feature
    basestations/
      get-basestations.feature
    rapps/
      catalogue.feature
      deploy-rapp.feature
      tune-rapp.feature
      disable-rapp.feature
      rollback-rapp.feature
    leaderboard/
      leaderboard.feature
    internal/
      get-active-sessions.feature
      push-event.feature
    health/
      health.feature
```

### JUnit 5 Runner

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KarateRunner {

    @LocalServerPort
    private int port;

    @Karate.Test
    Karate testAll() {
        return Karate.run("classpath:karate").relativeTo(getClass());
    }
}
```

### karate-config.js

```javascript
function fn() {
  return {
    baseUrl: 'http://localhost:' + karate.properties['server.port'],
    internalKey: 'test-internal-key'
  };
}
```

### Example Feature — Positive + Negative Scenarios

```gherkin
Feature: rApp Deployment

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/start-game.feature')

  Scenario: Deploy rApp returns 201 with DEPLOYING status
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = playerToken
    And request { templateId: 1, basestationId: #(basestationId) }
    When method POST
    Then status 201
    And match response.deployment.status == 'DEPLOYING'
    And match response.deployment.version == 1

  Scenario: Deploy to unowned basestation returns 403 FORBIDDEN
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = player2Token
    And request { templateId: 1, basestationId: #(player1BasestationId) }
    When method POST
    Then status 403
    And match response.error == 'FORBIDDEN'

  Scenario: Deploy with invalid templateId returns 404
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = playerToken
    And request { templateId: 999, basestationId: #(basestationId) }
    When method POST
    Then status 404

  Scenario: Rollback at version 1 returns 409 INVALID_STATE
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/rollback'
    And header X-Session-Token = playerToken
    When method PUT
    Then status 409
    And match response.error == 'INVALID_STATE'
```

### Required Scenarios Per Endpoint

| Endpoint | Positive | Negative |
|----------|----------|----------|
| POST /api/sessions | 201 with sessionCode + token | missing hostName → 400; name > 50 chars → 400 |
| POST /api/sessions/{code}/join | 200 with player token | not found → 404; full → 409; already ACTIVE → 409 |
| POST /api/sessions/{code}/start | 200 state = ACTIVE | non-host token → 403; 1 player → 409; already ACTIVE → 409 |
| GET /api/sessions/{code} | 200 with player list | not found → 404; wrong token → 403 |
| GET /api/sessions/{code}/basestations | 200 with metrics | game not started → 409; no auth → 401 |
| GET /api/rapps/catalogue | 200 with 7 rApps | no token → 401 |
| POST .../rapps/deploy | 201 DEPLOYING | unowned BS → 403; bad templateId → 404 |
| PUT .../rapps/{id}/tune | 200 version incremented | rApp not ACTIVE → 409; wrong player → 403 |
| PUT .../rapps/{id}/disable | 200 DISABLED | already DISABLED → 409; wrong player → 403 |
| PUT .../rapps/{id}/rollback | 200 version decremented | version 1 → 409; wrong player → 403 |
| GET .../leaderboard | 200 ranked entries | session not found → 404 |
| GET /api/internal/sessions/active | 200 sessions list | missing internal key → 401 |
| POST /api/internal/sessions/{code}/events | 201 eventId | session not ACTIVE → 409; bad basestationId → 404 |
| GET /actuator/health/readiness | 200 status UP | — |
| GET /actuator/health/liveness | 200 status UP | — |

### Run

```bash
# Karate starts the embedded server automatically via @SpringBootTest
cd backend && ./mvnw verify
```

---

## 3. E2E Testing - Selenium (Minimal)

### Scope

Two smoke tests only. API and controller correctness live at the Karate layer. Selenium confirms the full stack renders correctly in a real browser.

### Dependencies

```xml
<dependency>
    <groupId>org.seleniumhq.selenium</groupId>
    <artifactId>selenium-java</artifactId>
    <version>4.21.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.github.bonigarcia</groupId>
    <artifactId>webdrivermanager</artifactId>
    <version>5.9.1</version>
    <scope>test</scope>
</dependency>
```

### Base Class

```java
@Tag("e2e")
public abstract class BaseE2ETest {

    protected WebDriver driver;
    protected static final String FRONTEND_URL =
        System.getProperty("e2e.frontend.url", "http://localhost:3000");

    @BeforeAll static void setupDriver() { WebDriverManager.chromedriver().setup(); }

    @BeforeEach void openBrowser() {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
        driver = new ChromeDriver(opts);
    }

    @AfterEach void closeBrowser() { if (driver != null) driver.quit(); }
}
```

### Tests to Implement

**LobbyE2ETest:**
1. Navigate to frontend
2. Enter host name, click "Create Session"
3. Assert 8-character session code is displayed
4. Join from a second tab with code + guest name
5. Assert both player names appear in the lobby list

**GameStartE2ETest:**
1. Create + join session programmatically via REST
2. Navigate to lobby as host in browser
3. Click "Start Game"
4. Assert game board renders (basestation tiles visible)
5. Assert leaderboard shows both players

### Excluding from Default Build

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludedGroups>e2e</excludedGroups>
        <argLine>-XX:+EnableDynamicAgentLoading</argLine>
    </configuration>
</plugin>
```

Run manually when both services are up:

```bash
./mvnw test -Dgroups="e2e" -De2e.frontend.url=http://localhost:3000
```

---

## 4. Test Configuration

`src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect

game:
  players:
    min: 2
    max: 6
  tick:
    interval: 5000
    total: 60
  escalation:
    max-level: 3
    auto-resolve-after: 5

internal:
  api-key: test-internal-key
```

---

## 5. Code Coverage (JaCoCo)

Reports generated at `target/site/jacoco/index.html` after `./mvnw test`.

| Layer | Line | Branch |
|-------|------|--------|
| `service/` | >= 85% | >= 75% |
| `simulation/` | >= 90% | >= 85% |
| `factory/` | >= 85% | >= 75% |
| Overall | >= 80% | >= 70% |

Add enforcement to the JaCoCo plugin to fail the build on breach:

```xml
<execution>
    <id>check</id>
    <goals><goal>check</goal></goals>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

---

## 6. CI Stages

```yaml
# Stage 1 - unit + repository slice + Karate integration (no browser)
- name: Test
  run: ./mvnw verify
  working-directory: backend

# Stage 2 - E2E smoke (main branch only, requires services running)
- name: E2E Smoke
  if: github.ref == 'refs/heads/main'
  run: ./mvnw test -Dgroups="e2e" -De2e.frontend.url=http://localhost:3000
  working-directory: backend
```

---

## 7. Out of Scope

| Concern | Notes |
|---------|-------|
| WebSocket message delivery | Add STOMP client tests (Karate supports WebSocket) when needed |
| Performance / load | Add Gatling or k6 separately |
| Frontend tests | Jest setup lives in `frontend/` |
| Security / penetration | Manual or DAST tooling |
| DB migration testing | Hibernate DDL manages schema in test profile; revisit if Flyway is added |
