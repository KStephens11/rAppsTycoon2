package com.rapptycoon.factory;

import java.math.BigDecimal;

public class TrafficBalancerBehaviour extends AbstractRappBehaviour {

    public TrafficBalancerBehaviour() {
        super(
                new BigDecimal("8"),    // health
                new BigDecimal("12"),   // customerExperience
                new BigDecimal("10"),   // cost
                new BigDecimal("-3"),   // energyEfficiency
                new BigDecimal("5"),    // automationReliability
                new BigDecimal("7")     // slaCompliance
        );
    }

    @Override
    public String getRappName() {
        return "Traffic Balancer";
    }
}
