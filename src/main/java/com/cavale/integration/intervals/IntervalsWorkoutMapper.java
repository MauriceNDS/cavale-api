package com.cavale.integration.intervals;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cavale.training.workout.WorkoutFlattener;
import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;

/**
 * Renders the canonical workout tree in the Intervals.icu workout-builder
 * text syntax, the same tree the .fit exporter encodes. One step per line
 * ("- Allure VMA 5/8 30s 110-120% Pace"). Quality allures target a range of
 * threshold pace (100% = the athlete's threshold configured on
 * intervals.icu), so the watch alerts on the athlete's own zones without
 * Cavale knowing absolute paces.
 *
 * <p>Loops are unrolled rather than written as "Nx" blocks: intervals.icu
 * does turn those into native FIT repeats, but a native repeat carries one
 * name for all its reps, so the watch cannot say whether you are on the fifth
 * or the seventh — and two identical 8× blocks in one session look exactly
 * alike. See {@link WorkoutFlattener}.
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
        for (WorkoutFlattener.FlatStep step : WorkoutFlattener.flatten(nodes)) {
            out.append("- ").append(stepLine(step)).append('\n');
        }
        return out.toString().strip();
    }

    private static String stepLine(WorkoutFlattener.FlatStep step) {
        Node node = step.node();
        StringBuilder line = new StringBuilder(cue(node, step.repLabel()));
        if (node.seconds() != null) {
            line.append(' ').append(duration(node.seconds()));
        }
        String target = node.allure() == null ? null : ALLURE_TARGET.get(node.allure());
        if (target != null) {
            line.append(' ').append(target);
        }
        return line.toString();
    }

    private static String cue(Node node, String repLabel) {
        String name = ALLURE_NAME.getOrDefault(node.allure(), "Étape");
        if (node.terrain() != null) {
            name += switch (node.terrain()) {
                case COTE -> " (côte)";
                case DESCENTE -> " (descente)";
                case PLAT -> "";
            };
        }
        // The rep number is the whole point of unrolling — keep it in the name,
        // which is what the watch puts on screen.
        return repLabel == null ? name : name + " " + repLabel;
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

}
