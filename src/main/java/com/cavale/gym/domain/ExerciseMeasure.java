package com.cavale.gym.domain;

/** How a set is quantified — decides which inputs the live workout shows. */
public enum ExerciseMeasure {
    /** reps × external load (kg) */
    WEIGHT_REPS,
    /** reps at body weight (optional added load) */
    BODYWEIGHT_REPS,
    /** hold/duration in seconds (gainage, mobilité) */
    SECONDS
}
