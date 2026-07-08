package com.cavale.training.dto;

import java.util.List;

import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.TrainingPlan;

public record PlanDetailResponse(PlanResponse plan, List<WeekResponse> weeks) {

    public static PlanDetailResponse from(TrainingPlan plan, List<PlanWeek> weeks) {
        return new PlanDetailResponse(PlanResponse.from(plan), weeks.stream().map(WeekResponse::from).toList());
    }
}
