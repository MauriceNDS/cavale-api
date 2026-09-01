package com.cavale.training.workout;

import java.util.List;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.workout.WorkoutStructure.Node;

/**
 * The ONE place a session's prescribed duration is computed. Every view, ring
 * and aggregation must come through here.
 *
 * <p>A RUN session carries two durations: the flat {@code durationMin} the
 * coach typed, and the stored {@code workout} structure derived from the
 * detail text. They drift — the parser's deterministic recovery defaults are
 * not the coach's arithmetic — and when different readers picked different
 * fields the same session showed 76′ on its card and 80′ in the week ring.
 * The structure wins: it is what the athlete actually runs, what the .fit
 * export contains, and what the session page renders block by block.
 * {@code durationMin} stays stored (it still seeds the parser's padding rule
 * and covers structureless sessions) but is never the displayed truth for a
 * RUN that has blocks.
 */
public final class SessionDuration {

    private SessionDuration() {
    }

    /** Seconds covered by a workout tree, recursing into loops. */
    public static int totalSeconds(List<Node> nodes) {
        int total = 0;
        for (Node node : nodes) {
            if (node.isRepeat()) {
                total += node.count() * totalSeconds(node.children());
            } else if (node.seconds() != null) {
                total += node.seconds();
            }
        }
        return total;
    }

    /** Prescribed seconds, or null when the session carries no duration at all. */
    public static Integer plannedSeconds(PlannedSession session) {
        if (session.getDiscipline() == Discipline.RUN) {
            int structure = totalSeconds(WorkoutJson.read(session.getWorkoutJson()));
            if (structure > 0) {
                return structure;
            }
        }
        return session.getDurationMin() != null ? session.getDurationMin() * 60 : null;
    }

    /** Prescribed minutes, rounded to the nearest minute; null when there is none. */
    public static Integer plannedMinutes(PlannedSession session) {
        Integer seconds = plannedSeconds(session);
        return seconds == null ? null : Math.round(seconds / 60f);
    }

    /**
     * How far the stored {@code durationMin} sits from the structure it is
     * supposed to summarise, in seconds; null when the session has no
     * structure to compare against. Drives the {@code validate_plan} invariant.
     */
    public static Integer durationDriftSeconds(PlannedSession session) {
        if (session.getDiscipline() != Discipline.RUN || session.getDurationMin() == null) {
            return null;
        }
        int structure = totalSeconds(WorkoutJson.read(session.getWorkoutJson()));
        return structure > 0 ? session.getDurationMin() * 60 - structure : null;
    }
}
