package com.rapptycoon.factory;

import java.math.BigDecimal;

public class AlarmNoiseReducerBehaviour extends AbstractRappBehaviour {

    public AlarmNoiseReducerBehaviour() {
        super(
                new BigDecimal("5"),    // health
                new BigDecimal("2"),    // customerExperience
                new BigDecimal("-5"),   // cost
                new BigDecimal("0"),    // energyEfficiency
                new BigDecimal("12"),   // automationReliability
                new BigDecimal("3")     // slaCompliance
        );
    }

    @Override
    public String getRappName() {
        return "Alarm Noise Reducer";
    }
}
