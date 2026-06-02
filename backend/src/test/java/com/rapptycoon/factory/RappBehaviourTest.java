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

/**
 * Tests all 7 rApp behaviour implementations at every aggressiveness level.
 * Verifies that MetricDeltas are calculated as base × multiplier (HALF_UP, scale 2).
 *
 * Aggressiveness multipliers:
 *   LOW      = 0.5x
 *   MODERATE = 1.0x
 *   HIGH     = 1.5x
 */
class RappBehaviourTest {

    // -------------------------------------------------------------------------
    // 1. Energy Saver
    //    Base: health=0, ce=-5, cost=-30, ee=+20, ar=0, sla=-3
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("EnergySaverBehaviour")
    class EnergySaverTests {

        private final EnergySaverBehaviour behaviour = new EnergySaverBehaviour();

        @Test
        @DisplayName("LOW (0.5x): energyEfficiency=+10, customerExperience=-2.50, cost=-15")
        void lowMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.LOW);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("-2.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("-15.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("-1.50"));
        }

        @Test
        @DisplayName("MODERATE (1.0x): energyEfficiency=+20, customerExperience=-5, cost=-30")
        void moderateMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.MODERATE);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("-5.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("-30.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("-3.00"));
        }

        @Test
        @DisplayName("HIGH (1.5x): energyEfficiency=+30, customerExperience=-7.50, cost=-45")
        void highMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.HIGH);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("-7.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("-45.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("-4.50"));
        }

        @Test
        @DisplayName("getRappName returns 'Energy Saver'")
        void rappName() {
            assertThat(behaviour.getRappName()).isEqualTo("Energy Saver");
        }
    }

    // -------------------------------------------------------------------------
    // 2. Capacity Optimiser
    //    Base: health=+5, ce=+15, cost=+20, ee=-5, ar=+5, sla=+10
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("CapacityOptimiserBehaviour")
    class CapacityOptimiserTests {

        private final CapacityOptimiserBehaviour behaviour = new CapacityOptimiserBehaviour();

        @Test
        @DisplayName("LOW (0.5x): health=+2.50, ce=+7.50, ee=-2.50")
        void lowMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.LOW);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-2.50"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("5.00"));
        }

        @Test
        @DisplayName("MODERATE (1.0x): health=+5, ce=+15, ee=-5")
        void moderateMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.MODERATE);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-5.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("HIGH (1.5x): health=+7.50, ce=+22.50, ee=-7.50")
        void highMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.HIGH);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("22.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-7.50"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("15.00"));
        }

        @Test
        @DisplayName("getRappName returns 'Capacity Optimiser'")
        void rappName() {
            assertThat(behaviour.getRappName()).isEqualTo("Capacity Optimiser");
        }
    }

    // -------------------------------------------------------------------------
    // 3. Fault Predictor
    //    Base: health=+15, ce=+5, cost=+10, ee=0, ar=+10, sla=+8
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("FaultPredictorBehaviour")
    class FaultPredictorTests {

        private final FaultPredictorBehaviour behaviour = new FaultPredictorBehaviour();

        @Test
        @DisplayName("LOW (0.5x): health=+7.50, ce=+2.50, ar=+5, sla=+4")
        void lowMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.LOW);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("4.00"));
        }

        @Test
        @DisplayName("MODERATE (1.0x): health=+15, ce=+5, ar=+10, sla=+8")
        void moderateMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.MODERATE);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("8.00"));
        }

        @Test
        @DisplayName("HIGH (1.5x): health=+22.50, ce=+7.50, ar=+15, sla=+12")
        void highMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.HIGH);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("22.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("12.00"));
        }

        @Test
        @DisplayName("getRappName returns 'Fault Predictor'")
        void rappName() {
            assertThat(behaviour.getRappName()).isEqualTo("Fault Predictor");
        }
    }

    // -------------------------------------------------------------------------
    // 4. SLA Guardian
    //    Base: health=+5, ce=+10, cost=+15, ee=-2, ar=+5, sla=+20
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("SlaGuardianBehaviour")
    class SlaGuardianTests {

        private final SlaGuardianBehaviour behaviour = new SlaGuardianBehaviour();

        @Test
        @DisplayName("LOW (0.5x): health=+2.50, sla=+10, ee=-1")
        void lowMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.LOW);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-1.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("MODERATE (1.0x): health=+5, sla=+20, ee=-2")
        void moderateMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.MODERATE);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-2.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("HIGH (1.5x): health=+7.50, sla=+30, ee=-3")
        void highMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.HIGH);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("22.50"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-3.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("getRappName returns 'SLA Guardian'")
        void rappName() {
            assertThat(behaviour.getRappName()).isEqualTo("SLA Guardian");
        }
    }

    // -------------------------------------------------------------------------
    // 5. Configuration Drift Detector
    //    Base: health=+10, ce=+3, cost=+5, ee=+2, ar=+15, sla=+5
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("ConfigDriftDetectorBehaviour")
    class ConfigDriftDetectorTests {

        private final ConfigDriftDetectorBehaviour behaviour = new ConfigDriftDetectorBehaviour();

        @Test
        @DisplayName("LOW (0.5x): health=+5, ar=+7.50, ee=+1")
        void lowMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.LOW);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("1.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("1.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("2.50"));
        }

        @Test
        @DisplayName("MODERATE (1.0x): health=+10, ar=+15, ee=+2")
        void moderateMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.MODERATE);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("2.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("5.00"));
        }

        @Test
        @DisplayName("HIGH (1.5x): health=+15, ar=+22.50, ee=+3")
        void highMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.HIGH);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("4.50"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("22.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("7.50"));
        }

        @Test
        @DisplayName("getRappName returns 'Configuration Drift Detector'")
        void rappName() {
            assertThat(behaviour.getRappName()).isEqualTo("Configuration Drift Detector");
        }
    }

    // -------------------------------------------------------------------------
    // 6. Traffic Balancer
    //    Base: health=+8, ce=+12, cost=+10, ee=-3, ar=+5, sla=+7
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("TrafficBalancerBehaviour")
    class TrafficBalancerTests {

        private final TrafficBalancerBehaviour behaviour = new TrafficBalancerBehaviour();

        @Test
        @DisplayName("LOW (0.5x): health=+4, ce=+6, ee=-1.50")
        void lowMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.LOW);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("4.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-1.50"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("3.50"));
        }

        @Test
        @DisplayName("MODERATE (1.0x): health=+8, ce=+12, ee=-3")
        void moderateMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.MODERATE);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("8.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("12.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-3.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("7.00"));
        }

        @Test
        @DisplayName("HIGH (1.5x): health=+12, ce=+18, ee=-4.50")
        void highMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.HIGH);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("12.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("18.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-4.50"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("10.50"));
        }

        @Test
        @DisplayName("getRappName returns 'Traffic Balancer'")
        void rappName() {
            assertThat(behaviour.getRappName()).isEqualTo("Traffic Balancer");
        }
    }

    // -------------------------------------------------------------------------
    // 7. Alarm Noise Reducer
    //    Base: health=+5, ce=+2, cost=-5, ee=0, ar=+12, sla=+3
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("AlarmNoiseReducerBehaviour")
    class AlarmNoiseReducerTests {

        private final AlarmNoiseReducerBehaviour behaviour = new AlarmNoiseReducerBehaviour();

        @Test
        @DisplayName("LOW (0.5x): health=+2.50, ar=+6, cost=-2.50")
        void lowMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.LOW);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("1.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("-2.50"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("1.50"));
        }

        @Test
        @DisplayName("MODERATE (1.0x): health=+5, ar=+12, cost=-5")
        void moderateMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.MODERATE);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("2.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("-5.00"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("12.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("3.00"));
        }

        @Test
        @DisplayName("HIGH (1.5x): health=+7.50, ar=+18, cost=-7.50")
        void highMultiplier() {
            MetricDeltas d = behaviour.calculateImpact(Aggressiveness.HIGH);
            assertThat(d.health()).isEqualByComparingTo(new BigDecimal("7.50"));
            assertThat(d.customerExperience()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(d.cost()).isEqualByComparingTo(new BigDecimal("-7.50"));
            assertThat(d.energyEfficiency()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(d.automationReliability()).isEqualByComparingTo(new BigDecimal("18.00"));
            assertThat(d.slaCompliance()).isEqualByComparingTo(new BigDecimal("4.50"));
        }

        @Test
        @DisplayName("getRappName returns 'Alarm Noise Reducer'")
        void rappName() {
            assertThat(behaviour.getRappName()).isEqualTo("Alarm Noise Reducer");
        }
    }

    // -------------------------------------------------------------------------
    // Cross-cutting: all behaviours return non-null at every level
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("All behaviours — non-null contract")
    class AllBehavioursTests {

        @Test
        @DisplayName("all 7 behaviours return non-null MetricDeltas at every aggressiveness level")
        void allBehavioursReturnNonNullImpactsAtEveryLevel() {
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
                for (Aggressiveness level : Aggressiveness.values()) {
                    MetricDeltas d = behaviour.calculateImpact(level);
                    assertThat(d).as("%s at %s", behaviour.getRappName(), level).isNotNull();
                    assertThat(d.health()).isNotNull();
                    assertThat(d.customerExperience()).isNotNull();
                    assertThat(d.cost()).isNotNull();
                    assertThat(d.energyEfficiency()).isNotNull();
                    assertThat(d.automationReliability()).isNotNull();
                    assertThat(d.slaCompliance()).isNotNull();
                }
                assertThat(behaviour.getRappName()).isNotBlank();
            }
        }
    }

    // -------------------------------------------------------------------------
    // RappBehaviourRegistry
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("RappBehaviourRegistry")
    class RegistryTests {

        private final RappBehaviourRegistry registry = new RappBehaviourRegistry();

        @Test
        @DisplayName("returns correct behaviour by name for all 7 rApps")
        void returnsAll7Behaviours() {
            assertThat(registry.getBehaviour("Energy Saver")).isInstanceOf(EnergySaverBehaviour.class);
            assertThat(registry.getBehaviour("Capacity Optimiser")).isInstanceOf(CapacityOptimiserBehaviour.class);
            assertThat(registry.getBehaviour("Fault Predictor")).isInstanceOf(FaultPredictorBehaviour.class);
            assertThat(registry.getBehaviour("SLA Guardian")).isInstanceOf(SlaGuardianBehaviour.class);
            assertThat(registry.getBehaviour("Configuration Drift Detector")).isInstanceOf(ConfigDriftDetectorBehaviour.class);
            assertThat(registry.getBehaviour("Traffic Balancer")).isInstanceOf(TrafficBalancerBehaviour.class);
            assertThat(registry.getBehaviour("Alarm Noise Reducer")).isInstanceOf(AlarmNoiseReducerBehaviour.class);
        }

        @Test
        @DisplayName("throws EntityNotFoundException for unknown rApp name")
        void throwsForUnknownName() {
            assertThatThrownBy(() -> registry.getBehaviour("Unknown rApp"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("No behaviour found for rApp");
        }
    }
}
