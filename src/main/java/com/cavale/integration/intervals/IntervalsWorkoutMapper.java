package com.cavale.integration.intervals;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;

/**
 * Renders the canonical workout tree in the Intervals.icu workout-builder
 * text syntax, the same tree the .fit exporter encodes. One step per line
 * ("- Allure VMA 3m 110-120% Pace"), loops as "Nx" blocks framed by blank
 * lines. Quality allures target a range of threshold pace (100% = the
 * athlete's threshold configured on intervals.icu), so the watch alerts on
 * the athlete's own zones without Cavale knowing absolute paces.
 */
@Component
public class IntervalsWorkoutMapper {

    private static final Map<Allure, String> ALLURE_NAME = Map.of(
            Allure.LENTE, "Récup",
            Allure.EF, "Allure EF",
            Allure.COURSE, "Allure Course",
            Allure.SEUIL60, "Allure Seuil 60",
            Allure.SEUIL30, "Allure Seuil 30",
            Allure.VMA, "Allure VMA",
            Allure.SPRINT, "Allure Sprint");

    /**
     * Percent-of-threshold-pace bands per allure. LENTE stays target-free —
     * recovery jogs are by feel, exactly like the REST steps of the .fit
     * export. The bands bracket the physiological intent (threshold ≈ 100%).
     */
    private static final Map<Allure, String> ALLURE_TARGET = Map.of(
            Allure.EF, "65-78% Pace",
            Allure.COURSE, "78-88% Pace",
            Allure.SEUIL60, "94-100% Pace",
            Allure.SEUIL30, "100-106% Pace",
            Allure.VMA, "110-120% Pace",
            Allure.SPRINT, "130-150% Pace");

    public String describe(List<Node> nodes) {
        StringBuilder out = new StringBuilder();
        appendNodes(out, nodes, false);
        return out.toString().strip();
    }

    private void appendNodes(StringBuilder out, List<Node> nodes, boolean insideRepeat) {
        for (Node node : nodes) {
            if (node.isRepeat()) {
                if (insideRepeat) {
                    // Intervals.icu loops don't nest — unroll the inner loop.
                    for (int i = 0; i < node.count(); i++) {
                        appendNodes(out, node.children(), true);
                    }
                } else {
                    blankLine(out);
                    out.append(node.count()).append("x\n");
                    appendNodes(out, node.children(), true);
                    blankLine(out);
                }
            } else {
                out.append("- ").append(stepLine(node)).append('\n');
            }
        }
    }

    private static String stepLine(Node node) {
        StringBuilder line = new StringBuilder(cue(node));
        if (node.seconds() != null) {
            line.append(' ').append(duration(node.seconds()));
        }
        String target = node.allure() == null ? null : ALLURE_TARGET.get(node.allure());
        if (target != null) {
            line.append(' ').append(target);
        }
        return line.toString();
    }

    private static String cue(Node node) {
        String name = ALLURE_NAME.getOrDefault(node.allure(), "Étape");
        if (node.terrain() != null) {
            name += switch (node.terrain()) {
                case COTE -> " (côte)";
                case DESCENTE -> " (descente)";
                case PLAT -> "";
            };
        }
        return name;
    }

    /** 480 → "8m", 90 → "1m30s", 45 → "45s" ("m" means minutes on intervals.icu). */
    private static String duration(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes == 0) {
            return rest + "s";
        }
        return rest == 0 ? minutes + "m" : minutes + "m" + rest + "s";
    }

    private static void blankLine(StringBuilder out) {
        if (!out.isEmpty() && !out.toString().endsWith("\n\n")) {
            out.append('\n');
        }
    }
}
