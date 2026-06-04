package com.rapptycoon.factory;

import java.math.BigDecimal;

public class SlaGuardianBehaviour extends AbstractRappBehaviour {

    public SlaGuardianBehaviour() {
        super(
                new BigDecimal("5"),    // health
                new BigDecimal("10"),   // customerExperience
                new BigDecimal("15"),   // cost
                new BigDecimal("-2"),   // energyEfficiency
                new BigDecimal("5"),    // automationReliability
                new BigDecimal("20")    // slaCompliance
        );
    }

    @Override
    public String getRappName() {
        return "SLA Guardian";
    }
}
