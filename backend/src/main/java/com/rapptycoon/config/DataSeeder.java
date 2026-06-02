package com.rapptycoon.config;

import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.RappTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {


    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RappTemplateRepository rappTemplateRepository;

    public DataSeeder(RappTemplateRepository rappTemplateRepository) {
        this.rappTemplateRepository = rappTemplateRepository;
    }

    @Override
    public void run(String... args) {
        if (rappTemplateRepository.count() > 0) {
            log.info("rApp catalogue already seeded ({} templates)", rappTemplateRepository.count());
            return;
        }

        log.info("Seeding rApp catalogue...");

        List<RappTemplate> templates = List.of(
                RappTemplate.builder()
                        .name("Energy Saver")
                        .purpose("Reduces power consumption across basestation cells")
                        .cost(new BigDecimal("50.00"))
                        .benefit("Lowers energy costs by optimising power usage during low-traffic periods")
                        .risk(new BigDecimal("25.00"))
                        .confidence(new BigDecimal("80.00"))
                        .sideEffects("May increase latency in high-load cells")
                        .impactHealth(ZERO)
                        .impactCustomerExperience(NEG_FIVE)
                        .impactCost(NEG_THIRTY)
                        .impactEnergyEfficiency(TWENTY)
                        .impactAutomationReliability(ZERO)
                        .impactSlaCompliance(NEG_THREE)
                        .build(),
                RappTemplate.builder()
                        .name("Capacity Optimiser")
                        .purpose("Dynamically allocates capacity based on demand patterns")
                        .cost(new BigDecimal("75.00"))
                        .benefit("Improves customer experience by reducing congestion")
                        .risk(FIFTEEN)
                        .confidence(new BigDecimal("85.00"))
                        .sideEffects("Higher operational cost during peak hours")
                        .impactHealth(FIVE)
                        .impactCustomerExperience(FIFTEEN)
                        .impactCost(TWENTY)
                        .impactEnergyEfficiency(NEG_FIVE)
                        .impactAutomationReliability(FIVE)
                        .impactSlaCompliance(TEN)
                        .build(),
                RappTemplate.builder()
                        .name("Fault Predictor")
                        .purpose("Predicts hardware failures before they occur")
                        .cost(new BigDecimal("60.00"))
                        .benefit("Reduces downtime by enabling proactive maintenance")
                        .risk(TWENTY)
                        .confidence(new BigDecimal("70.00"))
                        .sideEffects("False positives may trigger unnecessary maintenance")
                        .impactHealth(FIFTEEN)
                        .impactCustomerExperience(FIVE)
                        .impactCost(TEN)
                        .impactEnergyEfficiency(ZERO)
                        .impactAutomationReliability(TEN)
                        .impactSlaCompliance(EIGHT)
                        .build(),
                RappTemplate.builder()
                        .name("SLA Guardian")
                        .purpose("Monitors and enforces SLA compliance thresholds")
                        .cost(new BigDecimal("45.00"))
                        .benefit("Prevents SLA breaches by auto-adjusting network parameters")
                        .risk(TEN)
                        .confidence(new BigDecimal("90.00"))
                        .sideEffects("May over-prioritise SLA metrics at expense of cost")
                        .impactHealth(FIVE)
                        .impactCustomerExperience(TEN)
                        .impactCost(FIFTEEN)
                        .impactEnergyEfficiency(NEG_TWO)
                        .impactAutomationReliability(FIVE)
                        .impactSlaCompliance(TWENTY)
                        .build(),
                RappTemplate.builder()
                        .name("Configuration Drift Detector")
                        .purpose("Detects when basestation config deviates from baseline")
                        .cost(new BigDecimal("35.00"))
                        .benefit("Maintains consistency and prevents silent degradation")
                        .risk(FIVE)
                        .confidence(new BigDecimal("95.00"))
                        .sideEffects("Alert fatigue if thresholds are too sensitive")
                        .impactHealth(TEN)
                        .impactCustomerExperience(THREE)
                        .impactCost(FIVE)
                        .impactEnergyEfficiency(TWO)
                        .impactAutomationReliability(FIFTEEN)
                        .impactSlaCompliance(FIVE)
                        .build(),
                RappTemplate.builder()
                        .name("Traffic Balancer")
                        .purpose("Distributes traffic load across cells to prevent congestion")
                        .cost(new BigDecimal("65.00"))
                        .benefit("Improves overall network throughput and user experience")
                        .risk(TWENTY)
                        .confidence(new BigDecimal("75.00"))
                        .sideEffects("May cause brief handover interruptions during rebalancing")
                        .impactHealth(EIGHT)
                        .impactCustomerExperience(TWELVE)
                        .impactCost(TEN)
                        .impactEnergyEfficiency(NEG_THREE)
                        .impactAutomationReliability(FIVE)
                        .impactSlaCompliance(SEVEN)
                        .build(),
                RappTemplate.builder()
                        .name("Alarm Noise Reducer")
                        .purpose("Filters and correlates alarms to reduce noise")
                        .cost(new BigDecimal("30.00"))
                        .benefit("Reduces operator fatigue and highlights real issues")
                        .risk(FIFTEEN)
                        .confidence(new BigDecimal("80.00"))
                        .sideEffects("May suppress genuine alarms if correlation rules are too aggressive")
                        .impactHealth(FIVE)
                        .impactCustomerExperience(TWO)
                        .impactCost(NEG_FIVE)
                        .impactEnergyEfficiency(ZERO)
                        .impactAutomationReliability(TWELVE)
                        .impactSlaCompliance(THREE)
                        .build()
        );

        rappTemplateRepository.saveAll(templates);
        log.info("Seeded {} rApp templates", templates.size());
    }
}
