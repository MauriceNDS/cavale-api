package com.cavale.training.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;

public record SessionResponse(
        UUID id,
        UUID weekId,
        LocalDate date,
        int orderInDay,
        Discipline discipline,
        String title,
        String detail,
        String zone,
        Integer durationMin,
        Integer elevationM,
        Integer rpeMin,
        Integer rpeMax,
        SessionStatus status) {

    public static SessionResponse from(PlannedSession session) {
        return new SessionResponse(session.getId(), session.getWeek().getId(), session.getDate(),
                session.getOrderInDay(), session.getDiscipline(), session.getTitle(), session.getDetail(),
                session.getZone(), session.getDurationMin(), session.getElevationM(),
                session.getRpeMin(), session.getRpeMax(), session.getStatus());
    }
}
