package com.rapptycoon.repository;

import com.rapptycoon.model.Basestation;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BasestationRepositoryTest {

    @Autowired
    private BasestationRepository basestationRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    private Long playerId;

    @BeforeEach
    void setUp() {
        GameSession session = GameSession.builder()
                .sessionCode("BSTN0001")
                .state(GameSessionState.LOBBY)
                .createdAt(LocalDateTime.now())
                .build();
        Long sessionId = gameSessionRepository.save(session).getId();

        Player player = Player.builder()
                .sessionId(sessionId)
                .displayName("TestPlayer")
                .sessionToken("token-bs-test-001")
                .build();
        playerId = playerRepository.save(player).getId();
    }

    @Test
    void saveAndFindById() {
        Basestation bs = Basestation.builder()
                .playerId(playerId)
                .name("Tower Alpha")
                .positionX(10)
                .positionY(20)
                .build();

        Basestation saved = basestationRepository.save(bs);

        Optional<Basestation> found = basestationRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Tower Alpha");
        assertThat(found.get().getPositionX()).isEqualTo(10);
        assertThat(found.get().getPositionY()).isEqualTo(20);
    }

    @Test
    void findByPlayerIdReturnsAllBasestationsForPlayer() {
        Basestation bs1 = Basestation.builder()
                .playerId(playerId)
                .name("Tower Alpha")
                .positionX(0)
                .positionY(0)
                .build();
        Basestation bs2 = Basestation.builder()
                .playerId(playerId)
                .name("Tower Beta")
                .positionX(5)
                .positionY(5)
                .build();
        basestationRepository.save(bs1);
        basestationRepository.save(bs2);

        List<Basestation> basestations = basestationRepository.findByPlayerId(playerId);
        assertThat(basestations).hasSize(2);
        assertThat(basestations).extracting(Basestation::getName)
                .containsExactlyInAnyOrder("Tower Alpha", "Tower Beta");
    }

    @Test
    void findByPlayerIdReturnsEmptyListForPlayerWithNoBasestations() {
        List<Basestation> basestations = basestationRepository.findByPlayerId(99999L);
        assertThat(basestations).isEmpty();
    }

    @Test
    void defaultMetricValues() {
        Basestation bs = Basestation.builder()
                .playerId(playerId)
                .name("Default Tower")
                .positionX(0)
                .positionY(0)
                .build();

        Basestation saved = basestationRepository.save(bs);
        Optional<Basestation> found = basestationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        Basestation result = found.get();
        assertThat(result.getHealth()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getCustomerExperience()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getCost()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.getEnergyEfficiency()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getAutomationReliability()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getSlaCompliance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
