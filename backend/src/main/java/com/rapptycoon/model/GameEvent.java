package com.rapptycoon.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "basestation_id", nullable = false)
    private Long basestationId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private EventSeverity severity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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

    @Column(name = "resolved")
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "escalation_level")
    @Builder.Default
    private int escalationLevel = 0;

    @Column(name = "ticks_at_max_escalation")
    @Builder.Default
    private int ticksAtMaxEscalation = 0;
}
