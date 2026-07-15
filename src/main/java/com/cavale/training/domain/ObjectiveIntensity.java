package com.cavale.training.domain;

/**
 * How hard the athlete wants to chase an objective — the dial plan generation
 * (P13) and the guardrails (P15) read. BALANCE means a smoother progression
 * with conservative guardrails; PERFORMANCE means the most aggressive ramp
 * that still stays inside the injury guardrails.
 */
public enum ObjectiveIntensity {
    BALANCE,
    PERFORMANCE
}
