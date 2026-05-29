package com.rapptycoon.simulation;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.factory.RappBehaviour;
import com.rapptycoon.factory.RappBehaviourRegistry;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.*;
import com.rapptycoon.service.BasestationService;
import com.rapptycoon.service.EventService;
import com.rapptycoon.service.GameSessionService;
import com.rapptycoon.service.ScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameTickEngineTest {

    @Mock
    private GameSessionRepository gameSessionRepository;
    @Mock
    private RappDeploymentRepository rappDeploymentRepository;
    @Mock
    private GameEventRepository gameEventRepository;
    @Mock
    private BasestationRepository basestationRepository;
    @Mock
    private RappTemplateRepository rappTemplateRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private RappBehaviourRegistry rappBehaviourRegistry;
    @Mock
    private BasestationService basestationService;
    @Mock
    private EventService eventService;
    @Mock
    private ScoreService scoreService;
    @Mock
    private GameSessionService gameSessionService;
    @Mock
    private GameProperties gameProperties;

    @InjectMocks
    private GameTickEngine gameTickEngine;

    private GameProperties.Tick tickProps;
    private GameProperties.Escalation escalationProps;

    @BeforeEach
    void setUp() {
        tickProps = new GameProperties.Tick();
        tickProps.setInterval(5000);
        tickProps.setTotal(60);

        escalationProps = new GameProperties.Escalation();
        escalationProps.setMaxLevel(3);
        escalationProps.setAutoResolveAfter(5);
    }

    private GameSession createActiveSession(int currentTick) {
        return GameSession.builder()
                .id(1L)
                .sessionCode("ABCD1234")
                .state(GameSessionState.ACTIVE)
                .currentTick(currentTick)
                .build();
    }

    private Player createPlayer(Long id, Long sessionId) {
        return Player.builder()
                .id(id)
                .sessionId(sessionId)
                .displayName("Player" + id)
                .sessionToken("token" + id)
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
                .health(new BigDecimal("100.00"))
                .customerExperience(new BigDecimal("100.00"))
                .cost(new BigDecimal("0.00"))
                .energyEfficiency(new BigDecimal("100.00"))
                .automationReliability(new BigDecimal("100.00"))
                .slaCompliance(new BigDecimal("100.00"))
                .build();
    }

    @Test
    @DisplayName("processTick: activates DEPLOYING rApps after 1 tick")
    void processTick_activatesDeployingRapps() {
        GameSession session = createActiveSession(5);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        RappDeployment deployingRapp = RappDeployment.builder()
                .id(10L)
                .templateId(1L)
                .basestationId(1L)
                .playerId(1L)
                .status(DeploymentStatus.DEPLOYING)
                .version(1)
                .configuration("{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}")
                .build();

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(List.of(deployingRapp));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(Collections.emptyList());
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        assertThat(deployingRapp.getStatus()).isEqualTo(DeploymentStatus.ACTIVE);
        verify(rappDeploymentRepository).save(deployingRapp);
    }

    @Test
    @DisplayName("processTick: applies active rApp impacts to basestations")
    void processTick_appliesRappImpacts() {
        GameSession session = createActiveSession(5);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        RappDeployment activeRapp = RappDeployment.builder()
                .id(10L)
                .templateId(1L)
                .basestationId(1L)
                .playerId(1L)
                .status(DeploymentStatus.ACTIVE)
                .version(1)
                .configuration("{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}")
                .build();

        RappTemplate template = RappTemplate.builder()
                .id(1L)
                .name("Energy Saver")
                .build();

        MetricDeltas impact = new MetricDeltas(
                BigDecimal.ZERO, new BigDecimal("-5.00"), new BigDecimal("-30.00"),
                new BigDecimal("20.00"), BigDecimal.ZERO, new BigDecimal("-3.00")
        );

        RappBehaviour behaviour = mock(RappBehaviour.class);
        when(behaviour.calculateImpact(Aggressiveness.MODERATE)).thenReturn(impact);

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(List.of(activeRapp));
        when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(rappBehaviourRegistry.getBehaviour("Energy Saver")).thenReturn(behaviour);
        when(eventService.getUnresolvedEvents(1L)).thenReturn(Collections.emptyList());
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        verify(basestationService).updateMetrics(eq(1L), eq(impact));
    }

    @Test
    @DisplayName("processTick: applies event impacts with escalation and severity multiplier")
    void processTick_appliesEventImpactsWithMultipliers() {
        GameSession session = createActiveSession(5);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        GameEvent event = GameEvent.builder()
                .id(100L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .impactHealth(new BigDecimal("-15.00"))
                .impactCustomerExperience(BigDecimal.ZERO)
                .impactCost(new BigDecimal("25.00"))
                .impactEnergyEfficiency(new BigDecimal("-20.00"))
                .impactAutomationReliability(BigDecimal.ZERO)
                .impactSlaCompliance(BigDecimal.ZERO)
                .escalationLevel(1)
                .resolved(false)
                .build();

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(List.of(event));
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        // Escalation level 1 = x1.5, severity HIGH = x2.0, combined = x3.0
        MetricDeltas expectedImpact = new MetricDeltas(
                new BigDecimal("-45.00"),
                new BigDecimal("0.00"),
                new BigDecimal("75.00"),
                new BigDecimal("-60.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00")
        );
        verify(basestationService).updateMetrics(eq(1L), eq(expectedImpact));
    }

    @Test
    @DisplayName("processTick: escalates HIGH events every tick")
    void processTick_escalatesHighEventsEveryTick() {
        GameSession session = createActiveSession(7);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        GameEvent highEvent = GameEvent.builder()
                .id(100L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .impactHealth(new BigDecimal("-5.00"))
                .impactCustomerExperience(BigDecimal.ZERO)
                .impactCost(BigDecimal.ZERO)
                .impactEnergyEfficiency(BigDecimal.ZERO)
                .impactAutomationReliability(BigDecimal.ZERO)
                .impactSlaCompliance(BigDecimal.ZERO)
                .escalationLevel(0)
                .resolved(false)
                .build();

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(List.of(highEvent));
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        // HIGH severity escalates every tick
        verify(eventService).escalateEvent(100L);
    }

    @Test
    @DisplayName("processTick: escalates LOW events only every 3 ticks")
    void processTick_escalatesLowEventsEvery3Ticks() {
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        GameEvent lowEvent = GameEvent.builder()
                .id(100L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("INTERFERENCE")
                .severity(EventSeverity.LOW)
                .impactHealth(new BigDecimal("-2.00"))
                .impactCustomerExperience(BigDecimal.ZERO)
                .impactCost(BigDecimal.ZERO)
                .impactEnergyEfficiency(BigDecimal.ZERO)
                .impactAutomationReliability(BigDecimal.ZERO)
                .impactSlaCompliance(BigDecimal.ZERO)
                .escalationLevel(0)
                .resolved(false)
                .build();

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(List.of(lowEvent));
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Tick 6 (divisible by 3) - should escalate
        GameSession session6 = createActiveSession(6);
        gameTickEngine.processTick(session6);
        verify(eventService).escalateEvent(100L);

        // Tick 7 (not divisible by 3) - should NOT escalate
        reset(eventService);
        when(eventService.getUnresolvedEvents(1L)).thenReturn(List.of(lowEvent));
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        GameSession session7 = createActiveSession(7);
        gameTickEngine.processTick(session7);
        verify(eventService, never()).escalateEvent(anyLong());
    }

    @Test
    @DisplayName("processTick: resolves events when effective rApp is deployed")
    void processTick_resolvesEventsWithEffectiveRapp() {
        GameSession session = createActiveSession(5);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        GameEvent event = GameEvent.builder()
                .id(100L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .impactHealth(new BigDecimal("-15.00"))
                .impactCustomerExperience(BigDecimal.ZERO)
                .impactCost(BigDecimal.ZERO)
                .impactEnergyEfficiency(BigDecimal.ZERO)
                .impactAutomationReliability(BigDecimal.ZERO)
                .impactSlaCompliance(BigDecimal.ZERO)
                .escalationLevel(0)
                .resolved(false)
                .build();

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(List.of(event));
        when(eventService.checkEventResolution(1L)).thenReturn(List.of(event));
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        verify(eventService).resolveEvent(100L);
    }

    @Test
    @DisplayName("processTick: auto-resolves events at max escalation")
    void processTick_autoResolvesEventsAtMaxEscalation() {
        GameSession session = createActiveSession(5);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        GameEvent maxEvent = GameEvent.builder()
                .id(100L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("HARDWARE_FAILURE")
                .severity(EventSeverity.CRITICAL)
                .impactHealth(new BigDecimal("-20.00"))
                .impactCustomerExperience(BigDecimal.ZERO)
                .impactCost(BigDecimal.ZERO)
                .impactEnergyEfficiency(BigDecimal.ZERO)
                .impactAutomationReliability(BigDecimal.ZERO)
                .impactSlaCompliance(BigDecimal.ZERO)
                .escalationLevel(3)
                .ticksAtMaxEscalation(5)
                .resolved(false)
                .build();

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        // First call returns the event, second call (after escalation) still returns it
        when(eventService.getUnresolvedEvents(1L)).thenReturn(List.of(maxEvent));
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        verify(eventService).checkAutoResolve(100L);
    }

    @Test
    @DisplayName("processTick: recalculates all player scores")
    void processTick_recalculatesScores() {
        GameSession session = createActiveSession(5);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(Collections.emptyList());
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        verify(scoreService).recalculateAllScores(1L);
    }

    @Test
    @DisplayName("processTick: increments tick counter")
    void processTick_incrementsTickCounter() {
        GameSession session = createActiveSession(10);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(Collections.emptyList());
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        assertThat(session.getCurrentTick()).isEqualTo(11);
        verify(gameSessionRepository).save(session);
    }

    @Test
    @DisplayName("processTick: ends game when tick reaches total (60)")
    void processTick_endsGameWhenTickReachesTotal() {
        GameSession session = createActiveSession(59); // Will become 60 after increment
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(Collections.emptyList());
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        assertThat(session.getCurrentTick()).isEqualTo(60);
        verify(gameSessionService).endSession("ABCD1234");
    }

    @Test
    @DisplayName("processTick: does not end game when tick is below total")
    void processTick_doesNotEndGameBeforeTotal() {
        GameSession session = createActiveSession(30);
        Player player = createPlayer(1L, 1L);
        Basestation bs = createBasestation(1L, 1L);

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.DEPLOYING))
                .thenReturn(Collections.emptyList());
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(eventService.getUnresolvedEvents(1L)).thenReturn(Collections.emptyList());
        when(eventService.checkEventResolution(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getTick()).thenReturn(tickProps);
        when(gameProperties.getEscalation()).thenReturn(escalationProps);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));

        gameTickEngine.processTick(session);

        verify(gameSessionService, never()).endSession(anyString());
    }

    @Test
    @DisplayName("tick: does not process completed sessions")
    void tick_doesNotProcessCompletedSessions() {
        // Only ACTIVE sessions are fetched
        when(gameSessionRepository.findByState(GameSessionState.ACTIVE))
                .thenReturn(Collections.emptyList());

        gameTickEngine.tick();

        verify(scoreService, never()).recalculateAllScores(anyLong());
        verify(gameSessionRepository, never()).save(any(GameSession.class));
    }

    @Test
    @DisplayName("getEscalationMultiplier: returns correct values for each level")
    void getEscalationMultiplier_correctValues() {
        assertThat(gameTickEngine.getEscalationMultiplier(0))
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(gameTickEngine.getEscalationMultiplier(1))
                .isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(gameTickEngine.getEscalationMultiplier(2))
                .isEqualByComparingTo(new BigDecimal("2.0"));
        assertThat(gameTickEngine.getEscalationMultiplier(3))
                .isEqualByComparingTo(new BigDecimal("3.0"));
        // Beyond max still returns 3.0
        assertThat(gameTickEngine.getEscalationMultiplier(5))
                .isEqualByComparingTo(new BigDecimal("3.0"));
    }

    @Test
    @DisplayName("getSeverityMultiplier: returns correct values for each severity")
    void getSeverityMultiplier_correctValues() {
        assertThat(gameTickEngine.getSeverityMultiplier(EventSeverity.LOW))
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(gameTickEngine.getSeverityMultiplier(EventSeverity.MEDIUM))
                .isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(gameTickEngine.getSeverityMultiplier(EventSeverity.HIGH))
                .isEqualByComparingTo(new BigDecimal("2.0"));
        assertThat(gameTickEngine.getSeverityMultiplier(EventSeverity.CRITICAL))
                .isEqualByComparingTo(new BigDecimal("3.0"));
    }

    @Test
    @DisplayName("shouldEscalate: LOW escalates every 3 ticks")
    void shouldEscalate_lowEvery3Ticks() {
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.LOW, 0)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.LOW, 1)).isFalse();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.LOW, 2)).isFalse();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.LOW, 3)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.LOW, 6)).isTrue();
    }

    @Test
    @DisplayName("shouldEscalate: MEDIUM escalates every 2 ticks")
    void shouldEscalate_mediumEvery2Ticks() {
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.MEDIUM, 0)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.MEDIUM, 1)).isFalse();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.MEDIUM, 2)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.MEDIUM, 3)).isFalse();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.MEDIUM, 4)).isTrue();
    }

    @Test
    @DisplayName("shouldEscalate: HIGH and CRITICAL escalate every tick")
    void shouldEscalate_highAndCriticalEveryTick() {
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.HIGH, 0)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.HIGH, 1)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.HIGH, 7)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.CRITICAL, 0)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.CRITICAL, 1)).isTrue();
        assertThat(gameTickEngine.shouldEscalate(EventSeverity.CRITICAL, 99)).isTrue();
    }
}
