package com.cavale.training.dto;

import java.time.LocalDate;
import java.util.List;

import com.cavale.training.domain.SessionStatus;
import com.cavale.training.workout.WorkoutStructure;

import jakarta.validation.constraints.Min;

/** Partial update: only non-null fields are applied. */
public record UpdateSessionRequest(
        LocalDate date,
        @Min(0) Integer orderInDay,
        SessionStatus status,
        String comment,
        List<WorkoutStructure.Node> workout,
        /** GYM only: link (or re-link) the session to a program variant. */
        java.util.UUID templateVariantId) {
}
