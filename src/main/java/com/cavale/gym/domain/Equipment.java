package com.cavale.gym.domain;

import java.math.BigDecimal;

/** What the exercise needs — BODYWEIGHT ones double as no-gym alternatives. */
public enum Equipment {
    BARBELL(new BigDecimal("2.5")),
    DUMBBELL(new BigDecimal("2")),
    MACHINE(new BigDecimal("5")),
    /** Cable stack / pulley station. */
    CABLE(new BigDecimal("5")),
    /** Guided barbell — the Smith machine. */
    SMITH(new BigDecimal("2.5")),
    BODYWEIGHT(new BigDecimal("2.5")),
    BAND(new BigDecimal("2.5")),
    BOX(new BigDecimal("2.5"));

    private final BigDecimal defaultIncrementKg;

    Equipment(BigDecimal defaultIncrementKg) {
        this.defaultIncrementKg = defaultIncrementKg;
    }

    /**
     * Smallest load step this kit can actually make: a barbell takes a pair
     * of 1.25 kg discs, dumbbell racks climb in 2 kg, machine and cable
     * stacks in whole 5 kg plates. Overridable per exercise, because gyms
     * disagree.
     */
    public BigDecimal defaultIncrementKg() {
        return defaultIncrementKg;
    }
}
