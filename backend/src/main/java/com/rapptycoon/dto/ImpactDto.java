package com.rapptycoon.dto;

import java.math.BigDecimal;

public record ImpactDto(
        BigDecimal health,
        BigDecimal customerExperience,
        BigDecimal cost,
        BigDecimal energyEfficiency,
        BigDecimal automationReliability,
        BigDecimal slaCompliance
) {}
