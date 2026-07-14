package com.cavale.training.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
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
        Integer elevationM,
        Integer rpeMin,
        Integer rpeMax,
        SessionStatus status,
        ActivitySummary activity,
        List<WorkoutStructure.Node> workout,
        String structureNotes,
        UUID templateVariantId,
        String templateName,
        String variantLabel) {

    public static SessionResponse from(PlannedSession session) {
        return from(session, null);
    }

    public static SessionResponse from(PlannedSession session, Activity activity) {
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
                session.getComment(), session.getZone(), session.getDurationMin(), session.getElevationM(),
                session.getRpeMin(), session.getRpeMax(), session.getStatus(),
                activity != null ? ActivitySummary.from(activity) : null,
                workout, notes,
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTemplate().getName() : null,
                variant != null ? variant.getLabel() : null);
    }
}
