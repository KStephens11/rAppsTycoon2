package com.rapptycoon.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "rapp_template")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RappTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(name = "benefit", nullable = false, columnDefinition = "TEXT")
    private String benefit;

    @Column(name = "risk", nullable = false, precision = 5, scale = 2)
    private BigDecimal risk;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "side_effects", columnDefinition = "TEXT")
    private String sideEffects;

    @Column(name = "impact_health", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal impactHealth = new BigDecimal("0.00");

    @Column(name = "impact_customer_experience", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal impactCustomerExperience = new BigDecimal("0.00");

    @Column(name = "impact_cost", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal impactCost = new BigDecimal("0.00");

    @Column(name = "impact_energy_efficiency", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal impactEnergyEfficiency = new BigDecimal("0.00");

    @Column(name = "impact_automation_reliability", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal impactAutomationReliability = new BigDecimal("0.00");

    @Column(name = "impact_sla_compliance", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal impactSlaCompliance = new BigDecimal("0.00");
}
