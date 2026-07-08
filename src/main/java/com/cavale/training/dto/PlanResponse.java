package com.cavale.training.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.cavale.training.domain.PlanStatus;
import com.cavale.training.domain.TrainingPlan;

public record PlanResponse(
        UUID id,
        String name,
        String goal,
        PlanStatus status,
        LocalDate startDate,
        LocalDate endDate) {

    public static PlanResponse from(TrainingPlan plan) {
        return new PlanResponse(plan.getId(), plan.getName(), plan.getGoal(),
                plan.getStatus(), plan.getStartDate(), plan.getEndDate());
    }
}
