package com.rapptycoon.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "basestation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Basestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "position_x", nullable = false)
    private int positionX;

    @Column(name = "position_y", nullable = false)
    private int positionY;

    @Column(name = "health", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal health = new BigDecimal("100.00");

    @Column(name = "customer_experience", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal customerExperience = new BigDecimal("100.00");

    @Column(name = "cost", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cost = new BigDecimal("0.00");

    @Column(name = "energy_efficiency", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal energyEfficiency = new BigDecimal("100.00");

    @Column(name = "automation_reliability", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal automationReliability = new BigDecimal("100.00");

    @Column(name = "sla_compliance", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal slaCompliance = new BigDecimal("100.00");

    @Version
    private Long version;
}
