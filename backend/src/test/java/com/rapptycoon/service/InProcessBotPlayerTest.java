package com.rapptycoon.service;

import com.rapptycoon.model.*;
import com.rapptycoon.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InProcessBotPlayerTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private BasestationRepository basestationRepository;

    @Mock
    private RappDeploymentRepository rappDeploymentRepository;

    @Mock
    private GameEventRepository gameEventRepository;

    @Mock
    private RappTemplateRepository rappTemplateRepository;

    @Mock
    private RappService rappService;

    @Mock
    private SplittableRandom random;

    @InjectMocks
    private InProcessBotPlayer inProcessBotPlayer;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Default: random always returns 0.0 so bots always act (0.0 <= any actionChance)
        when(random.nextDouble()).thenReturn(0.0);
        inProcessBotPlayer.setRandom(random);
    }

    private Player createBotPlayer(Long id, String difficulty, BigDecimal money) {
        return Player.builder()
                .id(id)
                .sessionId(1L)
                .displayName("Bot-Alpha")
                .sessionToken("bot-token-" + id)
                .isBot(true)
                .difficulty(difficulty)
                .scoreMoney(money)
                .build();
    }

    private Player createHumanPlayer(Long id) {
        return Player.builder()
                .id(id)
                .sessionId(1L)
                .displayName("Human")
                .sessionToken("human-token-" + id)
                .isBot(false)
                .scoreMoney(new BigDecimal("1000.00"))
                .build();
    }

    private Basestation createBasestation(Long id, Long playerId) {
        return Basestation.builder()
                .id(id)
                .playerId(playerId)
                .name("BS-" + id)
                .positionX(100)
                .positionY(100)
                .health(new BigDecimal("90.00"))
                .customerExperience(new BigDecimal("85.00"))
                .cost(new BigDecimal("0.00"))
                .energyEfficiency(new BigDecimal("80.00"))
                .automationReliability(new BigDecimal("88.00"))
                .slaCompliance(new BigDecimal("92.00"))
                .build();
    }

    private Basestation createLowMetricsBasestation(Long id, Long playerId) {
        return Basestation.builder()
                .id(id)
                .playerId(playerId)
                .name("BS-Low-" + id)
                .positionX(200)
                .positionY(200)
                .health(new BigDecimal("60.00"))
                .customerExperience(new BigDecimal("55.00"))
                .cost(new BigDecimal("0.00"))
                .energyEfficiency(new BigDecimal("50.00"))
                .automationReliability(new BigDecimal("58.00"))
                .slaCompliance(new BigDecimal("62.00"))
                .build();
    }

    private GameEvent createActiveEvent(Long id, Long basestationId, String eventType, EventSeverity severity) {
        return GameEvent.builder()
                .id(id)
                .sessionId(1L)
                .basestationId(basestationId)
                .eventType(eventType)
                .severity(severity)
                .resolved(false)
                .escalationLevel(0)
                .build();
    }

    private RappTemplate createTemplate(Long id, String name, BigDecimal cost) {
        return RappTemplate.builder()
                .id(id)
                .name(name)
                .purpose("Test purpose")
                .cost(cost)
                .benefit("Test benefit")
                .risk(new BigDecimal("10.00"))
                .confidence(new BigDecimal("90.00"))
                .build();
    }

    @Nested
    @DisplayName("executeBotActions")
    class ExecuteBotActions {

        @Test
        @DisplayName("does nothing when no bot players exist in session")
        void doesNothingWithNoBots() {
            Player human = createHumanPlayer(1L);
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(human));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("only processes bot players, not human players")
        void processesOnlyBotPlayers() {
            Player human = createHumanPlayer(1L);
            Player bot = createBotPlayer(2L, "MEDIUM", new BigDecimal("1000.00"));
            Basestation bs = createBasestation(10L, 2L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(human, bot));
            when(basestationRepository.findByPlayerId(2L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // Should not try to look up basestations for the human player
            verify(basestationRepository, never()).findByPlayerId(1L);
        }

        @Test
        @DisplayName("continues to next bot if one bot action throws exception")
        void continuesOnBotError() {
            Player bot1 = createBotPlayer(1L, "MEDIUM", new BigDecimal("1000.00"));
            Player bot2 = createBotPlayer(2L, "MEDIUM", new BigDecimal("1000.00"));
            Basestation bs1 = createBasestation(10L, 1L);
            Basestation bs2 = createBasestation(11L, 2L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot1, bot2));
            when(basestationRepository.findByPlayerId(1L)).thenThrow(new RuntimeException("DB error"));
            when(basestationRepository.findByPlayerId(2L)).thenReturn(List.of(bs2));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(11L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(11L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            // Should not throw, should continue to bot2
            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(basestationRepository).findByPlayerId(2L);
        }
    }

    @Nested
    @DisplayName("event resolution priority")
    class EventResolution {

        @Test
        @DisplayName("deploys effective rApp to resolve active POWER_OUTAGE event")
        void deploysRappToResolveEvent() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createBasestation(10L, 1L);
            GameEvent event = createActiveEvent(100L, 10L, "POWER_OUTAGE", EventSeverity.HIGH);
            RappTemplate template = createTemplate(1L, "Energy Saver", new BigDecimal("100.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // POWER_OUTAGE maps to templateIds [1, 3] — should try 1 first
            verify(rappService).deploy("ABCD1234", "bot-token-1", 1L, 10L);
        }

        @Test
        @DisplayName("skips already deployed rApp and tries next effective one")
        void skipsAlreadyDeployedRapp() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createBasestation(10L, 1L);
            GameEvent event = createActiveEvent(100L, 10L, "POWER_OUTAGE", EventSeverity.HIGH);
            RappTemplate template3 = createTemplate(3L, "Fault Predictor", new BigDecimal("80.00"));

            // Template 1 is already deployed
            RappDeployment existingDeploy = RappDeployment.builder()
                    .id(50L)
                    .templateId(1L)
                    .basestationId(10L)
                    .playerId(1L)
                    .status(DeploymentStatus.ACTIVE)
                    .build();

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(List.of(existingDeploy));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(3L)).thenReturn(Optional.of(template3));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // Should skip template 1 (already deployed) and try template 3
            verify(rappService).deploy("ABCD1234", "bot-token-1", 3L, 10L);
        }

        @Test
        @DisplayName("does not deploy if bot cannot afford the rApp")
        void doesNotDeployWhenInsufficientFunds() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("50.00")); // Low money
            Basestation bs = createBasestation(10L, 1L);
            GameEvent event = createActiveEvent(100L, 10L, "POWER_OUTAGE", EventSeverity.HIGH);
            RappTemplate template1 = createTemplate(1L, "Energy Saver", new BigDecimal("100.00"));
            RappTemplate template3 = createTemplate(3L, "Fault Predictor", new BigDecimal("80.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template1));
            when(rappTemplateRepository.findById(3L)).thenReturn(Optional.of(template3));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("skips deployment that would create a conflict")
        void skipsConflictingDeployment() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createBasestation(10L, 1L);
            // HARDWARE_FAILURE maps to [3, 5]
            GameEvent event = createActiveEvent(100L, 10L, "HARDWARE_FAILURE", EventSeverity.MEDIUM);
            RappTemplate template5 = createTemplate(5L, "SLA Manager", new BigDecimal("100.00"));

            // Template 7 is deployed (conflicts with template 3 per CONFLICT_PAIRS: [3,7])
            RappDeployment existingDeploy = RappDeployment.builder()
                    .id(50L)
                    .templateId(7L)
                    .basestationId(10L)
                    .playerId(1L)
                    .status(DeploymentStatus.ACTIVE)
                    .build();

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(List.of(existingDeploy));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            // Template 3 conflicts with 7, so it should be skipped; template 5 has no conflicts
            RappTemplate template3 = createTemplate(3L, "Fault Predictor", new BigDecimal("80.00"));
            when(rappTemplateRepository.findById(3L)).thenReturn(Optional.of(template3));
            when(rappTemplateRepository.findById(5L)).thenReturn(Optional.of(template5));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // Should skip template 3 (conflicts with 7) and deploy template 5
            verify(rappService).deploy("ABCD1234", "bot-token-1", 5L, 10L);
            verify(rappService, never()).deploy(eq("ABCD1234"), anyString(), eq(3L), anyLong());
        }

        @Test
        @DisplayName("skips low-severity event if deployment would drop below minimum safe balance")
        void skipsLowSeverityEventWhenBalanceTooLow() {
            // Bot has 280 money, template costs 100, so after deploy = 180 which is below MINIMUM_SAFE_BALANCE (200)
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("280.00"));
            Basestation bs = createBasestation(10L, 1L);
            GameEvent event = createActiveEvent(100L, 10L, "POWER_OUTAGE", EventSeverity.LOW);
            RappTemplate template = createTemplate(1L, "Energy Saver", new BigDecimal("100.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
            // Template 3 also costs too much
            RappTemplate template3 = createTemplate(3L, "Fault Predictor", new BigDecimal("100.00"));
            when(rappTemplateRepository.findById(3L)).thenReturn(Optional.of(template3));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("deploys for HIGH severity event even if it drops below minimum safe balance")
        void deploysForHighSeverityEvenBelowSafeBalance() {
            // Bot has 280 money, template costs 100, after deploy = 180 < 200 safe balance
            // But event severity is HIGH, so bot should still deploy
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("280.00"));
            Basestation bs = createBasestation(10L, 1L);
            GameEvent event = createActiveEvent(100L, 10L, "POWER_OUTAGE", EventSeverity.HIGH);
            RappTemplate template = createTemplate(1L, "Energy Saver", new BigDecimal("100.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService).deploy("ABCD1234", "bot-token-1", 1L, 10L);
        }

        @Test
        @DisplayName("performs only one action per tick per bot")
        void oneActionPerTickPerBot() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("1000.00"));
            Basestation bs = createBasestation(10L, 1L);
            GameEvent event1 = createActiveEvent(100L, 10L, "POWER_OUTAGE", EventSeverity.HIGH);
            GameEvent event2 = createActiveEvent(101L, 10L, "TRAFFIC_SPIKE", EventSeverity.HIGH);
            RappTemplate template = createTemplate(1L, "Energy Saver", new BigDecimal("100.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event1, event2));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // Should only deploy once (one action per tick)
            verify(rappService, times(1)).deploy(anyString(), anyString(), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("preventative deployment")
    class PreventativeDeployment {

        @Test
        @DisplayName("deploys preventatively when money is above 300 and basestation metrics are low")
        void deploysPreventativelyForLowMetrics() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L); // avg metrics well below 85

            RappTemplate template = createTemplate(5L, "SLA Manager", new BigDecimal("90.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(5L)).thenReturn(Optional.of(template));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // Should attempt preventative deployment since avg metrics < 85 and money > 300
            verify(rappService).deploy("ABCD1234", "bot-token-1", 5L, 10L);
        }

        @Test
        @DisplayName("does not deploy preventatively when money is 300 or below")
        void doesNotDeployPreventativelyWhenLowMoney() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("300.00")); // Exactly 300, not > 300
            Basestation bs = createLowMetricsBasestation(10L, 1L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("does not deploy preventatively when all basestations have good metrics")
        void doesNotDeployPreventativelyForGoodMetrics() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            // Default basestation has all metrics >= 85
            Basestation bs = createBasestation(10L, 1L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("skips already deployed templates during preventative deployment")
        void skipsDeployedTemplatesForPreventative() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);

            // Template 5 is already deployed
            RappDeployment existingDeploy = RappDeployment.builder()
                    .id(50L)
                    .templateId(5L)
                    .basestationId(10L)
                    .playerId(1L)
                    .status(DeploymentStatus.ACTIVE)
                    .build();

            RappTemplate template4 = createTemplate(4L, "Self-Healing Controller", new BigDecimal("85.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(List.of(existingDeploy));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(4L)).thenReturn(Optional.of(template4));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // Should skip template 5 (already deployed) and try template 4 (next in candidate list)
            verify(rappService).deploy("ABCD1234", "bot-token-1", 4L, 10L);
        }
    }

    @Nested
    @DisplayName("conflict detection")
    class ConflictDetection {

        @Test
        @DisplayName("detects bidirectional conflict: template 1 conflicts with template 2")
        void detectsBidirectionalConflict() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);

            // Template 2 is already deployed (conflicts with template 1 per CONFLICT_PAIRS: [1,2])
            RappDeployment existingDeploy = RappDeployment.builder()
                    .id(50L)
                    .templateId(2L)
                    .basestationId(10L)
                    .playerId(1L)
                    .status(DeploymentStatus.ACTIVE)
                    .build();

            // Candidate list starts with 5, 4, 3, 7, 6, 2, 1 — template 5 should be selected
            RappTemplate template5 = createTemplate(5L, "SLA Manager", new BigDecimal("90.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(List.of(existingDeploy));
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(5L)).thenReturn(Optional.of(template5));

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            // Should pick template 5 (no conflict), not template 1 (conflicts with 2)
            verify(rappService).deploy("ABCD1234", "bot-token-1", 5L, 10L);
        }
    }

    @Nested
    @DisplayName("difficulty scaling")
    class DifficultyScaling {

        @Test
        @DisplayName("EASY bot skips action when random value exceeds 0.30 threshold")
        void easyBotSkipsWhenRandomExceedsThreshold() {
            Player bot = createBotPlayer(1L, "EASY", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            // Random returns 0.5 which exceeds EASY threshold of 0.30
            when(random.nextDouble()).thenReturn(0.5);

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("EASY bot acts when random value is within 0.30 threshold")
        void easyBotActsWhenRandomWithinThreshold() {
            Player bot = createBotPlayer(1L, "EASY", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);
            RappTemplate template = createTemplate(5L, "SLA Manager", new BigDecimal("90.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(5L)).thenReturn(Optional.of(template));
            // Random returns 0.2 which is within EASY threshold of 0.30
            when(random.nextDouble()).thenReturn(0.2);

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService).deploy("ABCD1234", "bot-token-1", 5L, 10L);
        }

        @Test
        @DisplayName("MEDIUM bot skips action when random value exceeds 0.50 threshold")
        void mediumBotSkipsWhenRandomExceedsThreshold() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            // Random returns 0.7 which exceeds MEDIUM threshold of 0.50
            when(random.nextDouble()).thenReturn(0.7);

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("MEDIUM bot acts when random value is within 0.50 threshold")
        void mediumBotActsWhenRandomWithinThreshold() {
            Player bot = createBotPlayer(1L, "MEDIUM", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);
            RappTemplate template = createTemplate(5L, "SLA Manager", new BigDecimal("90.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(5L)).thenReturn(Optional.of(template));
            // Random returns 0.3 which is within MEDIUM threshold of 0.50
            when(random.nextDouble()).thenReturn(0.3);

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService).deploy("ABCD1234", "bot-token-1", 5L, 10L);
        }

        @Test
        @DisplayName("HARD bot skips action when random value exceeds 0.75 threshold")
        void hardBotSkipsWhenRandomExceedsThreshold() {
            Player bot = createBotPlayer(1L, "HARD", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            // Random returns 0.9 which exceeds HARD threshold of 0.75
            when(random.nextDouble()).thenReturn(0.9);

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("HARD bot acts when random value is within 0.75 threshold")
        void hardBotActsWhenRandomWithinThreshold() {
            Player bot = createBotPlayer(1L, "HARD", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);
            RappTemplate template = createTemplate(5L, "SLA Manager", new BigDecimal("90.00"));

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.DEPLOYING))
                    .thenReturn(Collections.emptyList());
            when(rappTemplateRepository.findById(5L)).thenReturn(Optional.of(template));
            // Random returns 0.6 which is within HARD threshold of 0.75
            when(random.nextDouble()).thenReturn(0.6);

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService).deploy("ABCD1234", "bot-token-1", 5L, 10L);
        }

        @Test
        @DisplayName("bot with unrecognised difficulty defaults to MEDIUM (0.50 threshold)")
        void unknownDifficultyDefaultsToMedium() {
            Player bot = createBotPlayer(1L, "UNKNOWN", new BigDecimal("500.00"));
            Basestation bs = createLowMetricsBasestation(10L, 1L);

            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            // Random returns 0.6 which exceeds default MEDIUM threshold of 0.50
            when(random.nextDouble()).thenReturn(0.6);

            inProcessBotPlayer.executeBotActions(1L, "ABCD1234");

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
        }
    }
}
