package com.rapptycoon.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "player")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "session_token", unique = true, nullable = false, length = 64)
    private String sessionToken;

    @Column(name = "score_money", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal scoreMoney = new BigDecimal("1000.00");

    @Column(name = "score_satisfaction", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal scoreSatisfaction = new BigDecimal("100.00");

    @Column(name = "score_stability", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal scoreStability = new BigDecimal("100.00");

    @Column(name = "composite_score", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal compositeScore = new BigDecimal("0.00");

    @Column(name = "connected")
    @Builder.Default
    private boolean connected = false;

    @Version
    private Long version;
}
