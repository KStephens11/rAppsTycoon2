package com.rapptycoon.repository;

import com.rapptycoon.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class GameEventRepositoryTest {

    @Autowired
    private GameEventRepository gameEventRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private BasestationRepository basestationRepository;

    private Long sessionId;
    private Long basestationId;

    @BeforeEach
    void setUp() {
        GameSession session = GameSession.builder()
                .sessionCode("EVNT0001")
                .state(GameSessionState.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        sessionId = gameSessionRepository.save(session).getId();

        Player player = Player.builder()
                .sessionId(sessionId)
                .displayName("EventPlayer")
                .sessionToken("token-event-001")
                .build();
        Long playerId = playerRepository.save(player).getId();

        Basestation bs = Basestation.builder()
                .playerId(playerId)
                .name("Event Tower")
                .positionX(3)
                .positionY(4)
                .build();
        basestationId = basestationRepository.save(bs).getId();
    }

    @Test
    void saveAndFindById() {
        GameEvent event = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("HARDWARE_FAILURE")
                .severity(EventSeverity.HIGH)
                .description("A critical hardware failure occurred")
                .createdAt(LocalDateTime.now())
                .build();

        GameEvent saved = gameEventRepository.save(event);

        Optional<GameEvent> found = gameEventRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEventType()).isEqualTo("HARDWARE_FAILURE");
        assertThat(found.get().getSeverity()).isEqualTo(EventSeverity.HIGH);
        assertThat(found.get().isResolved()).isFalse();
    }

    @Test
    void findBySessionIdAndResolvedFalseReturnsOnlyUnresolvedEvents() {
        GameEvent unresolved1 = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("NETWORK_ISSUE")
                .severity(EventSeverity.MEDIUM)
                .resolved(false)
                .createdAt(LocalDateTime.now())
                .build();
        GameEvent unresolved2 = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .resolved(false)
                .createdAt(LocalDateTime.now())
                .build();
        GameEvent resolved = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("SOFTWARE_BUG")
                .severity(EventSeverity.LOW)
                .resolved(true)
                .resolvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        gameEventRepository.save(unresolved1);
        gameEventRepository.save(unresolved2);
        gameEventRepository.save(resolved);

        List<GameEvent> unresolvedEvents = gameEventRepository
                .findBySessionIdAndResolvedFalse(sessionId);
        assertThat(unresolvedEvents).hasSize(2);
        assertThat(unresolvedEvents).extracting(GameEvent::getEventType)
                .containsExactlyInAnyOrder("NETWORK_ISSUE", "POWER_OUTAGE");
    }

    @Test
    void findByBasestationIdAndResolvedFalseReturnsOnlyUnresolvedEventsForBasestation() {
        GameEvent unresolved = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("OVERHEATING")
                .severity(EventSeverity.CRITICAL)
                .resolved(false)
                .createdAt(LocalDateTime.now())
                .build();
        GameEvent resolved = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("MINOR_GLITCH")
                .severity(EventSeverity.LOW)
                .resolved(true)
                .resolvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        gameEventRepository.save(unresolved);
        gameEventRepository.save(resolved);

        List<GameEvent> unresolvedEvents = gameEventRepository
                .findByBasestationIdAndResolvedFalse(basestationId);
        assertThat(unresolvedEvents).hasSize(1);
        assertThat(unresolvedEvents.get(0).getEventType()).isEqualTo("OVERHEATING");
    }

    @Test
    void resolvedEventsAreExcludedFromUnresolvedQueries() {
        GameEvent resolved1 = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("RESOLVED_EVENT_1")
                .severity(EventSeverity.LOW)
                .resolved(true)
                .resolvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        GameEvent resolved2 = GameEvent.builder()
                .sessionId(sessionId)
                .basestationId(basestationId)
                .eventType("RESOLVED_EVENT_2")
                .severity(EventSeverity.MEDIUM)
                .resolved(true)
                .resolvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        gameEventRepository.save(resolved1);
        gameEventRepository.save(resolved2);

        List<GameEvent> unresolvedBySession = gameEventRepository
                .findBySessionIdAndResolvedFalse(sessionId);
        assertThat(unresolvedBySession).isEmpty();

        List<GameEvent> unresolvedByBasestation = gameEventRepository
                .findByBasestationIdAndResolvedFalse(basestationId);
        assertThat(unresolvedByBasestation).isEmpty();
    }
}
