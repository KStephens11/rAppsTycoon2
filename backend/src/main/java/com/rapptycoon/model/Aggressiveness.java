package com.rapptycoon.model;

public enum Aggressiveness {
    LOW(0.5),
    MODERATE(1.0),
    HIGH(1.5);

    private final double multiplier;

    Aggressiveness(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
