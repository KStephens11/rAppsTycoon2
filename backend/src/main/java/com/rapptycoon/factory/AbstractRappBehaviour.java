package com.rapptycoon.factory;

import com.rapptycoon.model.Aggressiveness;
import com.rapptycoon.model.MetricDeltas;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Base class for all rApp behaviour implementations.
 * Subclasses only need to provide their base metric deltas and name.
 * The impact calculation logic (applying the aggressiveness multiplier) is handled here.
 */
public abstract class AbstractRappBehaviour implements RappBehaviour {

    private final BigDecimal baseHealth;
    private final BigDecimal baseCustomerExperience;
    private final BigDecimal baseCost;
    private final BigDecimal baseEnergyEfficiency;
    private final BigDecimal baseAutomationReliability;
    private final BigDecimal baseSlaCompliance;

    protected AbstractRappBehaviour(BigDecimal baseHealth,
                                    BigDecimal baseCustomerExperience,
                                    BigDecimal baseCost,
                                    BigDecimal baseEnergyEfficiency,
                                    BigDecimal baseAutomationReliability,
                                    BigDecimal baseSlaCompliance) {
        this.baseHealth = baseHealth;
        this.baseCustomerExperience = baseCustomerExperience;
        this.baseCost = baseCost;
        this.baseEnergyEfficiency = baseEnergyEfficiency;
        this.baseAutomationReliability = baseAutomationReliability;
        this.baseSlaCompliance = baseSlaCompliance;
    }

    @Override
    public MetricDeltas calculateImpact(Aggressiveness aggressiveness) {
        BigDecimal multiplier = BigDecimal.valueOf(aggressiveness.getMultiplier());
        return new MetricDeltas(
                baseHealth.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                baseCustomerExperience.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                baseCost.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                baseEnergyEfficiency.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                baseAutomationReliability.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                baseSlaCompliance.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
        );
    }
}
