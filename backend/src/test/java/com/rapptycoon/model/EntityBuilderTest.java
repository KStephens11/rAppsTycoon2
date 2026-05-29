package com.rapptycoon.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EntityBuilderTest {

    @Test
    void buildGameSessionWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        GameSession session = GameSession.builder()
                .id(1L)
                .sessionCode("TEST1234")
                .state(GameSessionState.ACTIVE)
                .hostPlayerId(10L)
                .maxPlayers(4)
                .currentTick(5)
                .createdAt(now)
                .startedAt(now)
                .endedAt(now)
                .build();

        assertThat(session.getId()).isEqualTo(1L);
        assertThat(session.getSessionCode()).isEqualTo("TEST1234");
        assertThat(session.getState()).isEqualTo(GameSessionState.ACTIVE);
        assertThat(session.getHostPlayerId()).isEqualTo(10L);
        assertThat(session.getMaxPlayers()).isEqualTo(4);
        assertThat(session.getCurrentTick()).isEqualTo(5);
        assertThat(session.getCreatedAt()).isEqualTo(now);
        assertThat(session.getStartedAt()).isEqualTo(now);
        assertThat(session.getEndedAt()).isEqualTo(now);
    }

    @Test
    void buildPlayerWithDefaultScoreMoney() {
        Player player = Player.builder()
                .sessionId(1L)
                .displayName("TestPlayer")
                .sessionToken("token-123")
                .build();

        assertThat(player.getScoreMoney())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void buildBasestationWithDefaultMetrics() {
        Basestation bs = Basestation.builder()
                .playerId(1L)
                .name("Tower")
                .positionX(0)
                .positionY(0)
                .build();

        assertThat(bs.getHealth())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(bs.getCustomerExperience())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(bs.getCost())
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(bs.getEnergyEfficiency())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(bs.getAutomationReliability())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(bs.getSlaCompliance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void buildRappDeploymentWithDefaultVersion() {
        RappDeployment deployment = RappDeployment.builder()
                .templateId(1L)
                .basestationId(2L)
                .playerId(3L)
                .status(DeploymentStatus.DEPLOYING)
                .build();

        assertThat(deployment.getVersion()).isEqualTo(1);
    }

    @Test
    void buildGameEventWithDefaults() {
        GameEvent event = GameEvent.builder()
                .sessionId(1L)
                .basestationId(2L)
                .eventType("TEST_EVENT")
                .severity(EventSeverity.LOW)
                .build();

        assertThat(event.getEscalationLevel()).isEqualTo(0);
        assertThat(event.isResolved()).isFalse();
        assertThat(event.getImpactHealth())
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(event.getImpactCustomerExperience())
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(event.getImpactCost())
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }
}
