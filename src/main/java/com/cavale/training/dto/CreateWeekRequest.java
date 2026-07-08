package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cavale.training.domain.WeekType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWeekRequest(

        @Min(value = 1, message = "Week number starts at 1")
        int weekNumber,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @Size(max = 100)
        String phase,

        @NotNull(message = "Week type is required")
        WeekType weekType,

        BigDecimal targetVolumeKm,
        Integer targetElevationM,
        Integer targetLoadUa,
        String focus) {
}
