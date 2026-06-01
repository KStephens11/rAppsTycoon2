package com.rapptycoon.model;

import java.math.BigDecimal;

/**
 * Value object representing metric changes to be applied to a basestation.
 */
public record MetricDeltas(
        BigDecimal health,
        BigDecimal customerExperience,
        BigDecimal cost,
        BigDecimal energyEfficiency,
        BigDecimal automationReliability,
        BigDecimal slaCompliance
) {}
