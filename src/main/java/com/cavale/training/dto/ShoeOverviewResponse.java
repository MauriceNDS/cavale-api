package com.cavale.training.dto;

import java.math.BigDecimal;

/**
 * One row of the shoes page: the pair, its life in numbers, and its share of
 * the recent rotation. recentKm is the last 90 days — rotation share is
 * recentKm over the sum across pairs, computed by the client.
 */
public record ShoeOverviewResponse(
        ShoeResponse shoe,
        ShoeStatsResponse stats,
        BigDecimal recentKm) {
}
