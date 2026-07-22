package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One pair's life in numbers: how much it ran, when, at what pace, and its
 * last six months of use — enough to judge wear and rotation balance.
 */
public record ShoeStatsResponse(
        int runs,
        BigDecimal totalKm,
        int totalElevationM,
        LocalDate firstUsedOn,
        LocalDate lastUsedOn,
        /** Average moving pace over the pair's runs, seconds per km. */
        Integer avgPaceSecPerKm,
        /** Km per calendar month, oldest first, last 6 months including the current one. */
        List<MonthKm> monthlyKm) {

    public record MonthKm(String month, BigDecimal km) {
    }
}
