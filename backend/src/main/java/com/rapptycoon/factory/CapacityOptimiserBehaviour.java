package com.rapptycoon.factory;

import java.math.BigDecimal;

public class CapacityOptimiserBehaviour extends AbstractRappBehaviour {

    public CapacityOptimiserBehaviour() {
        super(
                new BigDecimal("5"),    // health
                new BigDecimal("15"),   // customerExperience
                new BigDecimal("20"),   // cost
                new BigDecimal("-5"),   // energyEfficiency
                new BigDecimal("5"),    // automationReliability
                new BigDecimal("10")    // slaCompliance
        );
    }

    @Override
    public String getRappName() {
        return "Capacity Optimiser";
    }
}
