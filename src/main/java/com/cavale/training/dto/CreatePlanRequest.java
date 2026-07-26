package com.cavale.training.dto;

import java.time.LocalDate;

import com.cavale.training.domain.PlanFocus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlanRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @Size(max = 500, message = "Goal must not exceed 500 characters")
        String goal,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        /** Desired running sessions per week — scaffold input, optional. */
        @Min(1) @Max(7)
        Integer runsPerWeek,

        /** Desired strength sessions per week — scaffold input, optional. */
        @Min(0) @Max(4)
        Integer gymPerWeek,

        /** What the block optimizes for — MAINTAIN when omitted. */
        PlanFocus focus,

        /**
         * Full details of the MAIN objective the season is built around.
         * Optional: when absent, a placeholder objective named after the goal
         * (or the plan) is created, to be refined on the objective page.
         */
        @Valid
        CreateObjectiveRequest objective) {

    /** Convenience for callers that only know the season shell (import, MCP…). */
    public CreatePlanRequest(String name, String goal, LocalDate startDate, LocalDate endDate) {
        this(name, goal, startDate, endDate, null, null, null, null);
    }

    /** Season shell + objective, no explicit preferences. */
    public CreatePlanRequest(String name, String goal, LocalDate startDate, LocalDate endDate,
                             CreateObjectiveRequest objective) {
        this(name, goal, startDate, endDate, null, null, null, objective);
    }
}
