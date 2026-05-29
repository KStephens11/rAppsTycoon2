package com.rapptycoon.repository;

import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    private Long sessionId;

    @BeforeEach
    void setUp() {
        GameSession session = GameSession.builder()
                .sessionCode("SESS0001")
                .state(GameSessionState.LOBBY)
                .createdAt(LocalDateTime.now())
                .build();
        sessionId = gameSessionRepository.save(session).getId();
    }

    @Test
    void saveAndFindById() {
        Player player = Player.builder()
                .sessionId(sessionId)
                .displayName("Alice")
                .sessionToken("token-alice-001")
                .build();

        Player saved = playerRepository.save(player);

        Optional<Player> found = playerRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Alice");
        assertThat(found.get().getSessionToken()).isEqualTo("token-alice-001");
    }

    @Test
    void findBySessionIdReturnsAllPlayersInSession() {
        Player player1 = Player.builder()
                .sessionId(sessionId)
                .displayName("Alice")
                .sessionToken("token-alice-002")
                .build();
        Player player2 = Player.builder()
                .sessionId(sessionId)
                .displayName("Bob")
                .sessionToken("token-bob-001")
                .build();
        playerRepository.save(player1);
        playerRepository.save(player2);

        List<Player> players = playerRepository.findBySessionId(sessionId);
        assertThat(players).hasSize(2);
        assertThat(players).extracting(Player::getDisplayName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void findBySessionTokenReturnsCorrectPlayer() {
        Player player = Player.builder()
                .sessionId(sessionId)
                .displayName("Charlie")
                .sessionToken("token-charlie-001")
                .build();
        playerRepository.save(player);

        Optional<Player> found = playerRepository.findBySessionToken("token-charlie-001");
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Charlie");
    }

    @Test
    void findBySessionTokenReturnsEmptyForInvalidToken() {
        Optional<Player> found = playerRepository.findBySessionToken("invalid-token");
        assertThat(found).isEmpty();
    }

    @Test
    void uniqueConstraintOnSessionToken() {
        Player player1 = Player.builder()
                .sessionId(sessionId)
                .displayName("Dave")
                .sessionToken("duplicate-token")
                .build();
        playerRepository.saveAndFlush(player1);

        Player player2 = Player.builder()
                .sessionId(sessionId)
                .displayName("Eve")
                .sessionToken("duplicate-token")
                .build();

        assertThatThrownBy(() -> playerRepository.saveAndFlush(player2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
