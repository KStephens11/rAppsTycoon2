package com.rapptycoon.factory;

import java.math.BigDecimal;

public class EnergySaverBehaviour extends AbstractRappBehaviour {

    public EnergySaverBehaviour() {
        super(
                new BigDecimal("0"),    // health
                new BigDecimal("-5"),   // customerExperience
                new BigDecimal("-30"),  // cost
                new BigDecimal("20"),   // energyEfficiency
                new BigDecimal("0"),    // automationReliability
                new BigDecimal("-3")    // slaCompliance
        );
    }

    @Override
    public String getRappName() {
        return "Energy Saver";
    }
}
