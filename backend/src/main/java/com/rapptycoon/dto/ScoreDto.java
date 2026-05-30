package com.rapptycoon.dto;

import java.math.BigDecimal;

public record ScoreDto(
        BigDecimal money,
        BigDecimal customerSatisfaction,
        BigDecimal networkStability
) {}
