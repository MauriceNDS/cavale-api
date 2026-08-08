package com.cavale.training.workout;

import java.util.ArrayList;
import java.util.List;

import com.cavale.training.workout.WorkoutStructure.Node;

/**
 * Unrolls a workout tree into the flat sequence a watch actually walks
 * through, tagging every step with where it sits in its série.
 *
 * <p>A native "repeat 8×" block is compact, but on the wrist it collapses to
 * one nameless step you see eight times: mid-session there is no telling the
 * fifth rep from the seventh, and two identical 8× blocks in one workout are
 * indistinguishable. Unrolling costs steps and buys certainty — every rep
 * arrives carrying its own number.
 */
public final class WorkoutFlattener {

    private WorkoutFlattener() {
    }

    /**
     * One step of the unrolled workout.
     *
     * @param node     the step itself — allure, seconds, terrain
     * @param repLabel "(5/8)", or "(S2 5/8)" when the session runs several
     *                 séries, or null for a step outside any repeat
     */
    public record FlatStep(Node node, String repLabel) {
    }

    public static List<FlatStep> flatten(List<Node> nodes) {
        // Only qualify reps with a série number when there is more than one
        // série to confuse them with — "VMA 5/8" beats "VMA S1 5/8" alone.
        boolean numberSeries = countSeries(nodes) > 1;
        List<FlatStep> out = new ArrayList<>();
        appendNodes(nodes, out, numberSeries, new int[]{0}, null);
        return out;
    }

    private static void appendNodes(List<Node> nodes, List<FlatStep> out, boolean numberSeries,
                                    int[] seriesCounter, String inheritedLabel) {
        for (Node node : nodes) {
            if (!node.isRepeat()) {
                out.add(new FlatStep(node, inheritedLabel));
                continue;
            }
            int count = node.count() == null ? 1 : node.count();
            if (containsRepeat(node.children())) {
                // An outer loop around inner séries: unroll it, and let each
                // inner série claim its own number as it comes round.
                for (int i = 0; i < count; i++) {
                    appendNodes(node.children(), out, numberSeries, seriesCounter, inheritedLabel);
                }
                continue;
            }
            seriesCounter[0]++;
            String series = numberSeries ? "S" + seriesCounter[0] + " " : "";
            for (int rep = 1; rep <= count; rep++) {
                // The parentheses are load-bearing: intervals.icu's workout
                // parser reads a bare "5/8" as a number and silently turns the
                // step into a one-second nameless block.
                String label = "(" + series + rep + "/" + count + ")";
                for (Node child : node.children()) {
                    out.add(new FlatStep(child, label));
                }
            }
        }
    }

    /** How many innermost repeat blocks the session runs through. */
    private static int countSeries(List<Node> nodes) {
        int total = 0;
        for (Node node : nodes) {
            if (!node.isRepeat()) {
                continue;
            }
            int count = node.count() == null ? 1 : node.count();
            total += containsRepeat(node.children()) ? count * countSeries(node.children()) : 1;
        }
        return total;
    }

    private static boolean containsRepeat(List<Node> children) {
        return children != null && children.stream().anyMatch(Node::isRepeat);
    }
}
