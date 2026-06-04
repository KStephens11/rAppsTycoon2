package com.rapptycoon.factory;

import java.math.BigDecimal;

public class ConfigDriftDetectorBehaviour extends AbstractRappBehaviour {

    public ConfigDriftDetectorBehaviour() {
        super(
                new BigDecimal("10"),   // health
                new BigDecimal("3"),    // customerExperience
                new BigDecimal("5"),    // cost
                new BigDecimal("2"),    // energyEfficiency
                new BigDecimal("15"),   // automationReliability
                new BigDecimal("5")     // slaCompliance
        );
    }

    @Override
    public String getRappName() {
        return "Configuration Drift Detector";
    }
}
