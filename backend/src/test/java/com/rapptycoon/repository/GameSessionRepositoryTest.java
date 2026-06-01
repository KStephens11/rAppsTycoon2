package com.rapptycoon.repository;

import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class GameSessionRepositoryTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Test
    void saveAndFindById() {
        GameSession session = GameSession.builder()
                .sessionCode("ABCD1234")
                .state(GameSessionState.LOBBY)
                .maxPlayers(4)
                .createdAt(LocalDateTime.now())
                .build();

        GameSession saved = gameSessionRepository.save(session);

        Optional<GameSession> found = gameSessionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSessionCode()).isEqualTo("ABCD1234");
        assertThat(found.get().getState()).isEqualTo(GameSessionState.LOBBY);
        assertThat(found.get().getMaxPlayers()).isEqualTo(4);
    }

    @Test
    void findBySessionCodeReturnsCorrectSession() {
        GameSession session = GameSession.builder()
                .sessionCode("FIND1234")
                .state(GameSessionState.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        gameSessionRepository.save(session);

        Optional<GameSession> found = gameSessionRepository.findBySessionCode("FIND1234");
        assertThat(found).isPresent();
        assertThat(found.get().getState()).isEqualTo(GameSessionState.ACTIVE);
    }

    @Test
    void findBySessionCodeReturnsEmptyForNonExistentCode() {
        Optional<GameSession> found = gameSessionRepository.findBySessionCode("NOEXIST1");
        assertThat(found).isEmpty();
    }

    @Test
    void uniqueConstraintOnSessionCode() {
        GameSession session1 = GameSession.builder()
                .sessionCode("DUPL1234")
                .state(GameSessionState.LOBBY)
                .createdAt(LocalDateTime.now())
                .build();
        gameSessionRepository.saveAndFlush(session1);

        GameSession session2 = GameSession.builder()
                .sessionCode("DUPL1234")
                .state(GameSessionState.LOBBY)
                .createdAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> gameSessionRepository.saveAndFlush(session2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
