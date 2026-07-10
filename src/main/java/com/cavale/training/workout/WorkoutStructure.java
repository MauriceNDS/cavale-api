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
     * A workout node: either a single step or a repeat group.
     *
     * step   → {type:"step", label, durationSec?, zone?}
     * repeat → {type:"repeat", count, children[]} — children run in order,
     *          the whole group loops `count` times. Groups may nest
     *          (2 × (8 × 30″) → repeat(2, [repeat(8, [work, recover])])).
     */
    public record Node(String type, String label, Integer durationSec, String zone,
                       Integer count, List<Node> children) {

        public static Node step(String label, Integer durationSec, String zone) {
            return new Node("step", label, durationSec, zone, null, null);
        }

        public static Node repeat(int count, List<Node> children) {
            return new Node("repeat", null, null, null, count, List.copyOf(children));
        }

        public boolean isRepeat() {
            return "repeat".equals(type);
        }
    }

    public record Block(Section section, List<Node> nodes) {
    }
}
