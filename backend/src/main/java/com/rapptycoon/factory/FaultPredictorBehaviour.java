package com.rapptycoon.factory;

import java.math.BigDecimal;

public class FaultPredictorBehaviour extends AbstractRappBehaviour {

    public FaultPredictorBehaviour() {
        super(
                new BigDecimal("15"),   // health
                new BigDecimal("5"),    // customerExperience
                new BigDecimal("10"),   // cost
                new BigDecimal("0"),    // energyEfficiency
                new BigDecimal("10"),   // automationReliability
                new BigDecimal("8")     // slaCompliance
        );
    }

    @Override
    public String getRappName() {
        return "Fault Predictor";
    }
}
