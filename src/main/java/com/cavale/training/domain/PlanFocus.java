package com.cavale.training.domain;

/**
 * What a non-race (or between-races) season is optimizing for — drives the
 * default sessions the scaffold generates and the quality-work bias.
 */
public enum PlanFocus {
    /** Keep the engine running: balanced, unspectacular weeks. */
    MAINTAIN,
    /** Bias quality toward VMA/threshold — get faster. */
    SPEED,
    /** Bias volume and long-run growth — go longer. */
    ENDURANCE
}
