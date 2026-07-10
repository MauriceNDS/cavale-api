package com.cavale.training.workout;

import java.util.List;

/** Structured view of a session's description — feeds both the UI and .fit export. */
public final class WorkoutStructure {

    private WorkoutStructure() {
    }

    public enum Section {
        WARMUP, MAIN, COOLDOWN
    }

    /**
     * One executable piece of the workout.
     *
     * @param label       cleaned human text ("8 × 30″ en côte 8–10 % à intensité VMA")
     * @param repeats     total repetition count when the step is repeated (2×8 → 16), null otherwise
     * @param repeatLabel human form of the repetition ("2 × 8"), null when not repeated
     * @param durationSec duration of ONE repetition (or of the whole step when not repeated)
     * @param zone        detected pace zone (EF, VMA, Seuil 60…), null if none
     */
    public record Step(String label, Integer repeats, String repeatLabel, Integer durationSec, String zone) {
    }

    public record Block(Section section, List<Step> steps) {
    }
}
