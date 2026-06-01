package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.ImpactDto;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private GameEventRepository gameEventRepository;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private BasestationRepository basestationRepository;

    @Mock
    private RappDeploymentRepository rappDeploymentRepository;

    @Mock
    private RappTemplateRepository rappTemplateRepository;

    @Mock
    private GameProperties gameProperties;

    @InjectMocks
    private EventService eventService;

    private GameSession activeSession;
    private Basestation basestation;
    private ImpactDto sampleImpact;

    @BeforeEach
    void setUp() {
        activeSession = GameSession.builder()
                .id(1L)
                .sessionCode("ABCD1234")
                .state(GameSessionState.ACTIVE)
                .hostPlayerId(1L)
                .createdAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .build();

        basestation = Basestation.builder()
                .id(1L)
                .playerId(1L)
                .name("BS-Alpha")
                .positionX(100)
                .positionY(200)
                .health(new BigDecimal("100.00"))
                .customerExperience(new BigDecimal("100.00"))
                .cost(new BigDecimal("0.00"))
                .energyEfficiency(new BigDecimal("100.00"))
                .automationReliability(new BigDecimal("100.00"))
                .slaCompliance(new BigDecimal("100.00"))
                .build();

        sampleImpact = new ImpactDto(
                new BigDecimal("-15.00"),
                new BigDecimal("-10.00"),
                new BigDecimal("25.00"),
                new BigDecimal("-20.00"),
                new BigDecimal("-5.00"),
                new BigDecimal("-12.00")
        );
    }

    // --- createEvent tests ---

    @Test
    @DisplayName("createEvent creates event with correct fields")
    void createEvent_createsWithCorrectFields() {
        when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(activeSession));
        when(basestationRepository.findById(1L)).thenReturn(Optional.of(basestation));
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> {
            GameEvent event = invocation.getArgument(0);
            event.setId(5L);
            return event;
        });

        GameEvent result = eventService.createEvent("ABCD1234", 1L, "POWER_OUTAGE", "HIGH",
                "Power failure at BS-Alpha", sampleImpact);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getSessionId()).isEqualTo(1L);
        assertThat(result.getBasestationId()).isEqualTo(1L);
        assertThat(result.getEventType()).isEqualTo("POWER_OUTAGE");
        assertThat(result.getSeverity()).isEqualTo(EventSeverity.HIGH);
        assertThat(result.getDescription()).isEqualTo("Power failure at BS-Alpha");
        assertThat(result.getImpactHealth()).isEqualByComparingTo(new BigDecimal("-15.00"));
        assertThat(result.getImpactCustomerExperience()).isEqualByComparingTo(new BigDecimal("-10.00"));
        assertThat(result.getImpactCost()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.isResolved()).isFalse();
        assertThat(result.getEscalationLevel()).isEqualTo(0);
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("createEvent throws when session not found")
    void createEvent_throwsWhenSessionNotFound() {
        when(gameSessionRepository.findBySessionCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.createEvent("INVALID", 1L, "POWER_OUTAGE", "HIGH", "desc", sampleImpact))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("createEvent throws when session not ACTIVE")
    void createEvent_throwsWhenSessionNotActive() {
        GameSession lobbySession = GameSession.builder()
                .id(2L)
                .sessionCode("LOBBY123")
                .state(GameSessionState.LOBBY)
                .build();
        when(gameSessionRepository.findBySessionCode("LOBBY123")).thenReturn(Optional.of(lobbySession));

        assertThatThrownBy(() -> eventService.createEvent("LOBBY123", 1L, "POWER_OUTAGE", "HIGH", "desc", sampleImpact))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("not active");
    }

    // --- resolveEvent tests ---

    @Test
    @DisplayName("resolveEvent sets resolved=true and resolvedAt")
    void resolveEvent_setsResolvedAndTimestamp() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .escalationLevel(1)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(gameEventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameEvent result = eventService.resolveEvent(5L);

        assertThat(result.isResolved()).isTrue();
        assertThat(result.getResolvedAt()).isNotNull();
    }

    // --- escalateEvent tests ---

    @Test
    @DisplayName("escalateEvent increments escalation level")
    void escalateEvent_incrementsLevel() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .escalationLevel(1)
                .ticksAtMaxEscalation(0)
                .createdAt(LocalDateTime.now())
                .build();

        GameProperties.Escalation escalation = new GameProperties.Escalation();
        escalation.setMaxLevel(3);
        when(gameProperties.getEscalation()).thenReturn(escalation);
        when(gameEventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameEvent result = eventService.escalateEvent(5L);

        assertThat(result.getEscalationLevel()).isEqualTo(2);
        assertThat(result.getTicksAtMaxEscalation()).isEqualTo(0);
    }

    @Test
    @DisplayName("escalateEvent doesn't exceed max level, increments ticksAtMaxEscalation instead")
    void escalateEvent_doesNotExceedMaxLevel() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .escalationLevel(3)
                .ticksAtMaxEscalation(2)
                .createdAt(LocalDateTime.now())
                .build();

        GameProperties.Escalation escalation = new GameProperties.Escalation();
        escalation.setMaxLevel(3);
        when(gameProperties.getEscalation()).thenReturn(escalation);
        when(gameEventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameEvent result = eventService.escalateEvent(5L);

        assertThat(result.getEscalationLevel()).isEqualTo(3);
        assertThat(result.getTicksAtMaxEscalation()).isEqualTo(3);
    }

    @Test
    @DisplayName("escalateEvent throws when event is resolved")
    void escalateEvent_throwsWhenResolved() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .resolved(true)
                .build();

        when(gameEventRepository.findById(5L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.escalateEvent(5L))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("resolved");
    }

    // --- checkAutoResolve tests ---

    @Test
    @DisplayName("checkAutoResolve auto-resolves when at max for 5+ ticks")
    void checkAutoResolve_resolvesWhenAtMaxFor5Ticks() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .escalationLevel(3)
                .ticksAtMaxEscalation(5)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();

        GameProperties.Escalation escalation = new GameProperties.Escalation();
        escalation.setMaxLevel(3);
        escalation.setAutoResolveAfter(5);
        when(gameProperties.getEscalation()).thenReturn(escalation);
        when(gameEventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(basestationRepository.findById(1L)).thenReturn(Optional.of(basestation));
        when(basestationRepository.save(any(Basestation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = eventService.checkAutoResolve(5L);

        assertThat(result).isTrue();
        assertThat(event.isResolved()).isTrue();
        assertThat(event.getResolvedAt()).isNotNull();

        // Verify permanent damage was applied
        ArgumentCaptor<Basestation> bsCaptor = ArgumentCaptor.forClass(Basestation.class);
        verify(basestationRepository).save(bsCaptor.capture());
        Basestation savedBs = bsCaptor.getValue();
        assertThat(savedBs.getHealth()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(savedBs.getCustomerExperience()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(savedBs.getEnergyEfficiency()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(savedBs.getAutomationReliability()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(savedBs.getSlaCompliance()).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("checkAutoResolve doesn't auto-resolve when ticksAtMax < 5")
    void checkAutoResolve_doesNotResolveWhenTicksLessThan5() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .escalationLevel(3)
                .ticksAtMaxEscalation(4)
                .createdAt(LocalDateTime.now())
                .build();

        GameProperties.Escalation escalation = new GameProperties.Escalation();
        escalation.setMaxLevel(3);
        escalation.setAutoResolveAfter(5);
        when(gameProperties.getEscalation()).thenReturn(escalation);
        when(gameEventRepository.findById(5L)).thenReturn(Optional.of(event));

        boolean result = eventService.checkAutoResolve(5L);

        assertThat(result).isFalse();
        assertThat(event.isResolved()).isFalse();
        verify(basestationRepository, never()).save(any());
    }

    // --- checkEventResolution tests ---

    @Test
    @DisplayName("checkEventResolution detects effective rApp for POWER_OUTAGE")
    void checkEventResolution_detectsEffectiveRapp() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .createdAt(LocalDateTime.now())
                .build();

        RappDeployment deployment = RappDeployment.builder()
                .id(10L)
                .templateId(1L)
                .basestationId(1L)
                .playerId(1L)
                .status(DeploymentStatus.ACTIVE)
                .build();

        RappTemplate template = RappTemplate.builder()
                .id(1L)
                .name("Energy Saver")
                .build();

        when(gameEventRepository.findByBasestationIdAndResolvedFalse(1L)).thenReturn(List.of(event));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(List.of(deployment));
        when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        List<GameEvent> resolvable = eventService.checkEventResolution(1L);

        assertThat(resolvable).hasSize(1);
        assertThat(resolvable.get(0).getEventType()).isEqualTo("POWER_OUTAGE");
    }

    @Test
    @DisplayName("checkEventResolution returns empty when no effective rApp deployed")
    void checkEventResolution_returnsEmptyWhenNoEffectiveRapp() {
        GameEvent event = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .createdAt(LocalDateTime.now())
                .build();

        RappDeployment deployment = RappDeployment.builder()
                .id(10L)
                .templateId(4L)
                .basestationId(1L)
                .playerId(1L)
                .status(DeploymentStatus.ACTIVE)
                .build();

        RappTemplate template = RappTemplate.builder()
                .id(4L)
                .name("SLA Guardian")
                .build();

        when(gameEventRepository.findByBasestationIdAndResolvedFalse(1L)).thenReturn(List.of(event));
        when(rappDeploymentRepository.findByBasestationIdAndStatus(1L, DeploymentStatus.ACTIVE))
                .thenReturn(List.of(deployment));
        when(rappTemplateRepository.findById(4L)).thenReturn(Optional.of(template));

        List<GameEvent> resolvable = eventService.checkEventResolution(1L);

        assertThat(resolvable).isEmpty();
    }

    // --- getUnresolvedEvents tests ---

    @Test
    @DisplayName("getUnresolvedEvents returns only unresolved events")
    void getUnresolvedEvents_returnsOnlyUnresolved() {
        GameEvent unresolvedEvent = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .resolved(false)
                .build();

        when(gameEventRepository.findBySessionIdAndResolvedFalse(1L)).thenReturn(List.of(unresolvedEvent));

        List<GameEvent> result = eventService.getUnresolvedEvents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isResolved()).isFalse();
    }
}
