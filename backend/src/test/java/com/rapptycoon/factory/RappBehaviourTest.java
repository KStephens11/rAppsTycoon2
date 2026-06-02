package com.rapptycoon.factory;

import com.rapptycoon.model.Aggressiveness;
import com.rapptycoon.model.MetricDeltas;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RappBehaviourTest {

    @Nested
    @DisplayName("EnergySaverBehaviour")
    class EnergySaverTests {

        private final EnergySaverBehaviour behaviour = new EnergySaverBehaviour();

        @Test
        @DisplayName("returns correct base impacts with MODERATE aggressiveness")
        void returnsCorrectBaseImpactsWithModerate() {
            MetricDeltas impact = behaviour.calculateImpact(Aggressiveness.MODERATE);

            assertThat(impact.health()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(impact.customerExperience()).isEqualByComparingTo(new BigDecimal("-5.00"));
            assertThat(impact.cost()).isEqualByComparingTo(new BigDecimal("-30.00"));
            assertThat(impact.energyEfficiency()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(impact.automationReliability()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(impact.slaCompliance()).isEqualByComparingTo(new BigDecimal("-3.00"));
        }

        @Test
        @DisplayName("applies LOW multiplier (0.5x)")
        void appliesLowMultiplier() {
            MetricDeltas impact = behaviour.calculateImpact(Aggressiveness.LOW);

            assertThat(impact.health()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(impact.customerExperience()).isEqualByComparingTo(new BigDecimal("-2.50"));
            assertThat(impact.cost()).isEqualByComparingTo(new BigDecimal("-15.00"));
            assertThat(impact.energyEfficiency()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(impact.automationReliability()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(impact.slaCompliance()).isEqualByComparingTo(new BigDecimal("-1.50"));
        }

        @Test
        @DisplayName("applies HIGH multiplier (1.5x)")
        void appliesHighMultiplier() {
            MetricDeltas impact = behaviour.calculateImpact(Aggressiveness.HIGH);

            assertThat(impact.health()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(impact.customerExperience()).isEqualByComparingTo(new BigDecimal("-7.50"));
            assertThat(impact.cost()).isEqualByComparingTo(new BigDecimal("-45.00"));
            assertThat(impact.energyEfficiency()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(impact.automationReliability()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(impact.slaCompliance()).isEqualByComparingTo(new BigDecimal("-4.50"));
        }

        @Test
        @DisplayName("returns correct rApp name")
        void returnsCorrectName() {
            assertThat(behaviour.getRappName()).isEqualTo("Energy Saver");
        }
    }

    @Nested
    @DisplayName("CapacityOptimiserBehaviour")
    class CapacityOptimiserTests {

        private final CapacityOptimiserBehaviour behaviour = new CapacityOptimiserBehaviour();

        @Test
        @DisplayName("returns correct impacts with MODERATE aggressiveness")
        void returnsCorrectImpactsWithModerate() {
            MetricDeltas impact = behaviour.calculateImpact(Aggressiveness.MODERATE);

            assertThat(impact.health()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(impact.customerExperience()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(impact.cost()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(impact.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-5.00"));
            assertThat(impact.automationReliability()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(impact.slaCompliance()).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("returns correct rApp name")
        void returnsCorrectName() {
            assertThat(behaviour.getRappName()).isEqualTo("Capacity Optimiser");
        }
    }

    @Nested
    @DisplayName("All behaviours")
    class AllBehavioursTests {

        @Test
        @DisplayName("all 7 behaviours return non-null impacts with MODERATE")
        void allBehavioursReturnNonNullImpacts() {
            List<RappBehaviour> behaviours = List.of(
                    new EnergySaverBehaviour(),
                    new CapacityOptimiserBehaviour(),
                    new FaultPredictorBehaviour(),
                    new SlaGuardianBehaviour(),
                    new ConfigDriftDetectorBehaviour(),
                    new TrafficBalancerBehaviour(),
                    new AlarmNoiseReducerBehaviour()
            );

            for (RappBehaviour behaviour : behaviours) {
                MetricDeltas impact = behaviour.calculateImpact(Aggressiveness.MODERATE);
                assertThat(impact).isNotNull();
                assertThat(impact.health()).isNotNull();
                assertThat(impact.customerExperience()).isNotNull();
                assertThat(impact.cost()).isNotNull();
                assertThat(impact.energyEfficiency()).isNotNull();
                assertThat(impact.automationReliability()).isNotNull();
                assertThat(impact.slaCompliance()).isNotNull();
                assertThat(behaviour.getRappName()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("RappBehaviourRegistry")
    class RegistryTests {

        private final RappBehaviourRegistry registry = new RappBehaviourRegistry();

        @Test
        @DisplayName("returns correct behaviour by name")
        void returnsCorrectBehaviourByName() {
            RappBehaviour behaviour = registry.getBehaviour("Energy Saver");

            assertThat(behaviour).isInstanceOf(EnergySaverBehaviour.class);
            assertThat(behaviour.getRappName()).isEqualTo("Energy Saver");
        }

        @Test
        @DisplayName("returns all 7 registered behaviours")
        void returnsAll7Behaviours() {
            assertThat(registry.getBehaviour("Energy Saver")).isNotNull();
            assertThat(registry.getBehaviour("Capacity Optimiser")).isNotNull();
            assertThat(registry.getBehaviour("Fault Predictor")).isNotNull();
            assertThat(registry.getBehaviour("SLA Guardian")).isNotNull();
            assertThat(registry.getBehaviour("Configuration Drift Detector")).isNotNull();
            assertThat(registry.getBehaviour("Traffic Balancer")).isNotNull();
            assertThat(registry.getBehaviour("Alarm Noise Reducer")).isNotNull();
        }

        @Test
        @DisplayName("throws EntityNotFoundException for unknown name")
        void throwsForUnknownName() {
            assertThatThrownBy(() -> registry.getBehaviour("Unknown rApp"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("No behaviour found for rApp");
        }
    }
}
