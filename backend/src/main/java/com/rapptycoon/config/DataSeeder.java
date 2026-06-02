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
                        .impactHealth(new BigDecimal("0.00"))
                        .impactCustomerExperience(new BigDecimal("-5.00"))
                        .impactCost(new BigDecimal("-30.00"))
                        .impactEnergyEfficiency(new BigDecimal("20.00"))
                        .impactAutomationReliability(new BigDecimal("0.00"))
                        .impactSlaCompliance(new BigDecimal("-3.00"))
                        .build(),
                RappTemplate.builder()
                        .name("Capacity Optimiser")
                        .purpose("Dynamically allocates capacity based on demand patterns")
                        .cost(new BigDecimal("75.00"))
                        .benefit("Improves customer experience by reducing congestion")
                        .risk(new BigDecimal("15.00"))
                        .confidence(new BigDecimal("85.00"))
                        .sideEffects("Higher operational cost during peak hours")
                        .impactHealth(new BigDecimal("5.00"))
                        .impactCustomerExperience(new BigDecimal("15.00"))
                        .impactCost(new BigDecimal("20.00"))
                        .impactEnergyEfficiency(new BigDecimal("-5.00"))
                        .impactAutomationReliability(new BigDecimal("5.00"))
                        .impactSlaCompliance(new BigDecimal("10.00"))
                        .build(),
                RappTemplate.builder()
                        .name("Fault Predictor")
                        .purpose("Predicts hardware failures before they occur")
                        .cost(new BigDecimal("60.00"))
                        .benefit("Reduces downtime by enabling proactive maintenance")
                        .risk(new BigDecimal("20.00"))
                        .confidence(new BigDecimal("70.00"))
                        .sideEffects("False positives may trigger unnecessary maintenance")
                        .impactHealth(new BigDecimal("15.00"))
                        .impactCustomerExperience(new BigDecimal("5.00"))
                        .impactCost(new BigDecimal("10.00"))
                        .impactEnergyEfficiency(new BigDecimal("0.00"))
                        .impactAutomationReliability(new BigDecimal("10.00"))
                        .impactSlaCompliance(new BigDecimal("8.00"))
                        .build(),
                RappTemplate.builder()
                        .name("SLA Guardian")
                        .purpose("Monitors and enforces SLA compliance thresholds")
                        .cost(new BigDecimal("45.00"))
                        .benefit("Prevents SLA breaches by auto-adjusting network parameters")
                        .risk(new BigDecimal("10.00"))
                        .confidence(new BigDecimal("90.00"))
                        .sideEffects("May over-prioritise SLA metrics at expense of cost")
                        .impactHealth(new BigDecimal("5.00"))
                        .impactCustomerExperience(new BigDecimal("10.00"))
                        .impactCost(new BigDecimal("15.00"))
                        .impactEnergyEfficiency(new BigDecimal("-2.00"))
                        .impactAutomationReliability(new BigDecimal("5.00"))
                        .impactSlaCompliance(new BigDecimal("20.00"))
                        .build(),
                RappTemplate.builder()
                        .name("Configuration Drift Detector")
                        .purpose("Detects when basestation config deviates from baseline")
                        .cost(new BigDecimal("35.00"))
                        .benefit("Maintains consistency and prevents silent degradation")
                        .risk(new BigDecimal("5.00"))
                        .confidence(new BigDecimal("95.00"))
                        .sideEffects("Alert fatigue if thresholds are too sensitive")
                        .impactHealth(new BigDecimal("10.00"))
                        .impactCustomerExperience(new BigDecimal("3.00"))
                        .impactCost(new BigDecimal("5.00"))
                        .impactEnergyEfficiency(new BigDecimal("2.00"))
                        .impactAutomationReliability(new BigDecimal("15.00"))
                        .impactSlaCompliance(new BigDecimal("5.00"))
                        .build(),
                RappTemplate.builder()
                        .name("Traffic Balancer")
                        .purpose("Distributes traffic load across cells to prevent congestion")
                        .cost(new BigDecimal("65.00"))
                        .benefit("Improves overall network throughput and user experience")
                        .risk(new BigDecimal("20.00"))
                        .confidence(new BigDecimal("75.00"))
                        .sideEffects("May cause brief handover interruptions during rebalancing")
                        .impactHealth(new BigDecimal("8.00"))
                        .impactCustomerExperience(new BigDecimal("12.00"))
                        .impactCost(new BigDecimal("10.00"))
                        .impactEnergyEfficiency(new BigDecimal("-3.00"))
                        .impactAutomationReliability(new BigDecimal("5.00"))
                        .impactSlaCompliance(new BigDecimal("7.00"))
                        .build(),
                RappTemplate.builder()
                        .name("Alarm Noise Reducer")
                        .purpose("Filters and correlates alarms to reduce noise")
                        .cost(new BigDecimal("30.00"))
                        .benefit("Reduces operator fatigue and highlights real issues")
                        .risk(new BigDecimal("15.00"))
                        .confidence(new BigDecimal("80.00"))
                        .sideEffects("May suppress genuine alarms if correlation rules are too aggressive")
                        .impactHealth(new BigDecimal("5.00"))
                        .impactCustomerExperience(new BigDecimal("2.00"))
                        .impactCost(new BigDecimal("-5.00"))
                        .impactEnergyEfficiency(new BigDecimal("0.00"))
                        .impactAutomationReliability(new BigDecimal("12.00"))
                        .impactSlaCompliance(new BigDecimal("3.00"))
                        .build()
        );

        rappTemplateRepository.saveAll(templates);
        log.info("Seeded {} rApp templates", templates.size());
    }
}
