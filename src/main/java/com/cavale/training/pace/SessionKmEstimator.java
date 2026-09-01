package com.cavale.training.pace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.workout.SessionDuration;
import com.cavale.training.workout.WorkoutJson;
import com.cavale.training.workout.WorkoutParser;
import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;

/**
 * Converts a time-prescribed running session into expected kilometres: each
 * workout block runs at the model's pace for its allure, then the session's
 * planned D+ eats into the distance (climbing seconds produce no horizontal
 * kilometres). Sessions are prescribed in minutes, so this is the only place
 * the plan's "minutes" and the week's "km" currencies meet.
 */
public final class SessionKmEstimator {

    /** Elevation can never erase more than half the flat distance — beyond
     *  that the D+/duration combination is inconsistent, not informative. */
    private static final double MAX_CLIMB_SHRINK = 0.5;

    /** Structure shorter than the session by at least this much means the main
     *  run lives outside the parsed blocks — same threshold as the parser. */
    private static final int PADDING_GAP_SEC = 20 * 60;

    private SessionKmEstimator() {
    }

    /** Expected km for a RUN session, or null when there is nothing to estimate. */
    public static BigDecimal estimateKm(PlannedSession session, PaceModel model) {
        if (session.getDiscipline() != Discipline.RUN) {
            return null;
        }
        List<Node> nodes = WorkoutJson.read(session.getWorkoutJson());
        if (nodes.isEmpty()) {
            if (session.getDurationMin() == null) {
                return null;
            }
            nodes = List.of(Node.step(WorkoutParser.allureOfZone(session.getZone()),
                    session.getDurationMin() * 60, null));
        }

        double flatKm = flatKm(nodes, model);
        int totalSec = SessionDuration.totalSeconds(nodes);

        // Pre-structure sessions whose blocks cover far less than the planned
        // duration: the missing time is easy running (parser guarantees this
        // for new sessions; this covers older stored structures).
        if (session.getDurationMin() != null) {
            int gap = session.getDurationMin() * 60 - totalSec;
            if (gap >= PADDING_GAP_SEC) {
                flatKm += (double) gap / model.secPerKm(Allure.EF);
                totalSec += gap;
            }
        }
        if (totalSec <= 0 || flatKm <= 0) {
            return null;
        }

        int elevation = session.getElevationM() != null ? session.getElevationM() : 0;
        double climbShare = model.climbSecPerMeter() * elevation / totalSec;
        double km = flatKm * Math.max(MAX_CLIMB_SHRINK, 1 - climbShare);
        return BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
    }

    private static double flatKm(List<Node> nodes, PaceModel model) {
        double km = 0;
        for (Node node : nodes) {
            if (node.isRepeat()) {
                km += node.count() * flatKm(node.children(), model);
            } else if (node.seconds() != null) {
                km += (double) node.seconds() / model.secPerKm(node.allure());
            }
        }
        return km;
    }
}
