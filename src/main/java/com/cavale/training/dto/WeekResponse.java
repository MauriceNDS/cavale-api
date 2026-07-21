package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.WeekType;

public record WeekResponse(
        UUID id,
        int weekNumber,
        LocalDate startDate,
        String phase,
        WeekType weekType,
        BigDecimal targetVolumeKm,
        /** Km the prescribed session times should actually produce (personal pace model). */
        BigDecimal estimatedVolumeKm,
        Integer targetElevationM,
        Integer targetLoadUa,
        String focus) {

    public static WeekResponse from(PlanWeek week) {
        return from(week, null);
    }

    public static WeekResponse from(PlanWeek week, BigDecimal estimatedVolumeKm) {
        return new WeekResponse(week.getId(), week.getWeekNumber(), week.getStartDate(), week.getPhase(),
                week.getWeekType(), week.getTargetVolumeKm(), estimatedVolumeKm, week.getTargetElevationM(),
                week.getTargetLoadUa(), week.getFocus());
    }
}
