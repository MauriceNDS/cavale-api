package com.cavale.training.workout;

import java.util.List;

/**
 * Canonical, deterministic workout model. A block always has an ALLURE as its
 * identity, a duration in seconds, and optionally a terrain. Loops repeat at
 * least two blocks (typically work + récupération). No prose ever lives here.
 */
public final class WorkoutStructure {

    private WorkoutStructure() {
    }

    public enum Allure {
        LENTE, EF, COURSE, SEUIL60, SEUIL30, VMA, SPRINT
    }

    public enum Terrain {
        PLAT, COTE, DESCENTE
    }

    /**
     * step   → {type:"step", allure, seconds, terrain}
     * repeat → {type:"repeat", count, children[]}
     */
    public record Node(String type, Allure allure, Integer seconds, Terrain terrain,
                       Integer count, List<Node> children) {

        public static Node step(Allure allure, Integer seconds, Terrain terrain) {
            return new Node("step", allure, seconds,
                    terrain == null || terrain == Terrain.PLAT ? null : terrain, null, null);
        }

        public static Node repeat(int count, List<Node> children) {
            return new Node("repeat", null, null, null, count, List.copyOf(children));
        }

        public boolean isRepeat() {
            return "repeat".equals(type);
        }
    }

    /** Import result: the strict structure plus every piece of prose (consignes). */
    public record Parsed(List<Node> nodes, String notes) {

        public static final Parsed EMPTY = new Parsed(List.of(), null);
    }
}
