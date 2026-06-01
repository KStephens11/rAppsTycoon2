package com.rapptycoon.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GamePropertiesTest {

    @Autowired
    private GameProperties gameProperties;

    @Test
    void tickIntervalIsLoadedFromTestProfile() {
        assertThat(gameProperties.getTick().getInterval()).isEqualTo(100);
    }

    @Test
    void tickTotalIsLoadedFromTestProfile() {
        assertThat(gameProperties.getTick().getTotal()).isEqualTo(5);
    }

    @Test
    void playersMinIsLoaded() {
        assertThat(gameProperties.getPlayers().getMin()).isEqualTo(2);
    }

    @Test
    void playersMaxIsLoaded() {
        assertThat(gameProperties.getPlayers().getMax()).isEqualTo(6);
    }

    @Test
    void basestationsPerPlayerIsLoaded() {
        assertThat(gameProperties.getBasestations().getPerPlayer()).isEqualTo(3);
    }

    @Test
    void scoreWeightMoneyIsLoaded() {
        assertThat(gameProperties.getScore().getWeight().getMoney()).isEqualTo(0.30);
    }

    @Test
    void scoreWeightSatisfactionIsLoaded() {
        assertThat(gameProperties.getScore().getWeight().getSatisfaction()).isEqualTo(0.35);
    }

    @Test
    void scoreWeightStabilityIsLoaded() {
        assertThat(gameProperties.getScore().getWeight().getStability()).isEqualTo(0.35);
    }

    @Test
    void eventsBaseRateIsLoaded() {
        assertThat(gameProperties.getEvents().getBaseRate()).isEqualTo(0.3);
    }

    @Test
    void escalationMaxLevelIsLoaded() {
        assertThat(gameProperties.getEscalation().getMaxLevel()).isEqualTo(3);
    }

    @Test
    void rappDeploymentTicksIsLoaded() {
        assertThat(gameProperties.getRapp().getDeploymentTicks()).isEqualTo(1);
    }
}
