package com.cavale.training.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
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
        List<WorkoutStructure.Block> structure,
        String structureNotes) {

    public static SessionResponse from(PlannedSession session) {
        return from(session, null);
    }

    public static SessionResponse from(PlannedSession session, Activity activity) {
        WorkoutStructure.Parsed parsed = session.getDiscipline() == Discipline.RUN
                ? WorkoutParser.parse(session.getDetail())
                : WorkoutStructure.Parsed.EMPTY;
        return new SessionResponse(session.getId(), session.getWeek().getId(), session.getDate(),
                session.getOrderInDay(), session.getDiscipline(), session.getTitle(), session.getDetail(),
                session.getComment(), session.getZone(), session.getDurationMin(), session.getElevationM(),
                session.getRpeMin(), session.getRpeMax(), session.getStatus(),
                activity != null ? ActivitySummary.from(activity) : null,
                parsed.blocks(), parsed.notes());
    }
}
