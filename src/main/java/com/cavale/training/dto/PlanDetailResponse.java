package com.cavale.training.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.TrainingPlan;

public record PlanDetailResponse(PlanResponse plan, List<WeekResponse> weeks) {

    public static PlanDetailResponse from(TrainingPlan plan, List<PlanWeek> weeks) {
        return from(plan, weeks, Map.of());
    }

    public static PlanDetailResponse from(TrainingPlan plan, List<PlanWeek> weeks,
                                          Map<UUID, BigDecimal> estimatedVolumeKmByWeek) {
        return new PlanDetailResponse(PlanResponse.from(plan), weeks.stream()
                .map(week -> WeekResponse.from(week, estimatedVolumeKmByWeek.get(week.getId())))
                .toList());
    }
}
