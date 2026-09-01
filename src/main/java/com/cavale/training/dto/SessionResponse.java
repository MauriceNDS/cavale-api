package com.cavale.training.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.workout.SessionDuration;
import com.cavale.training.workout.WorkoutJson;
import com.cavale.training.workout.WorkoutParser;
import com.cavale.training.workout.WorkoutStructure;

public record SessionResponse(
        UUID id,
        UUID weekId,
        LocalDate date,
        int orderInDay,
        Discipline discipline,
        String title,
        String detail,
        String comment,
        String zone,
        Integer durationMin,
        /**
         * The prescribed duration to DISPLAY: the stored workout structure's
         * own total for a RUN that has blocks, else {@code durationMin}.
         * Computed server-side so no view has to pick a field for itself.
         */
        Integer plannedDurationMin,
        Integer elevationM,
        Integer rpeMin,
        Integer rpeMax,
        SessionStatus status,
        /** Real minutes once DONE — the activity's for a run, the workout log's for gym. */
        Integer actualDurationMin,
        ActivitySummary activity,
        List<WorkoutStructure.Node> workout,
        String structureNotes,
        UUID templateVariantId,
        String templateName,
        String variantLabel) {

    public static SessionResponse from(PlannedSession session) {
        return from(session, null, null);
    }

    public static SessionResponse from(PlannedSession session, Activity activity) {
        return from(session, activity, null);
    }

    public static SessionResponse from(PlannedSession session, Activity activity, Integer gymDurationMin) {
        List<WorkoutStructure.Node> workout = List.of();
        String notes = null;
        if (session.getDiscipline() == Discipline.RUN) {
            // stored structure is the source of truth; parsing is import-time only
            workout = WorkoutJson.read(session.getWorkoutJson());
            notes = WorkoutParser.parse(session.getDetail(), session.getZone(), session.getDurationMin())
                    .notes();
        }
        var variant = session.getTemplateVariant(); // EAGER pair — safe outside the tx
        return new SessionResponse(session.getId(), session.getWeek().getId(), session.getDate(),
                session.getOrderInDay(), session.getDiscipline(), session.getTitle(), session.getDetail(),
                session.getComment(), session.getZone(), session.getDurationMin(),
                SessionDuration.plannedMinutes(session), session.getElevationM(),
                session.getRpeMin(), session.getRpeMax(), session.getStatus(),
                activity != null ? Integer.valueOf(activity.getDurationMin()) : gymDurationMin,
                activity != null ? ActivitySummary.from(activity) : null,
                workout, notes,
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTemplate().getName() : null,
                variant != null ? variant.getLabel() : null);
    }
}
