package com.rapptycoon.factory;

import com.rapptycoon.model.Aggressiveness;
import com.rapptycoon.model.MetricDeltas;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ConfigDriftDetectorBehaviour implements RappBehaviour {

    private static final BigDecimal BASE_HEALTH = new BigDecimal("10");
    private static final BigDecimal BASE_CUSTOMER_EXPERIENCE = new BigDecimal("3");
    private static final BigDecimal BASE_COST = new BigDecimal("5");
    private static final BigDecimal BASE_ENERGY_EFFICIENCY = new BigDecimal("2");
    private static final BigDecimal BASE_AUTOMATION_RELIABILITY = new BigDecimal("15");
    private static final BigDecimal BASE_SLA_COMPLIANCE = new BigDecimal("5");

    @Override
    public MetricDeltas calculateImpact(Aggressiveness aggressiveness) {
        BigDecimal multiplier = BigDecimal.valueOf(aggressiveness.getMultiplier());
        return new MetricDeltas(
                BASE_HEALTH.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                BASE_CUSTOMER_EXPERIENCE.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                BASE_COST.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                BASE_ENERGY_EFFICIENCY.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                BASE_AUTOMATION_RELIABILITY.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                BASE_SLA_COMPLIANCE.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Override
    public String getRappName() {
        return "Configuration Drift Detector";
    }
}
