package com.cavale.training.dto;

import java.time.LocalDate;
import java.util.List;

import com.cavale.training.domain.SessionStatus;
import com.cavale.training.workout.WorkoutStructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update: only non-null fields are applied. A blank {@code detail},
 * {@code zone} or {@code comment} clears the stored value; numeric fields
 * can only be replaced, not cleared. When the content of a RUN session
 * changes and no explicit {@code workout} is sent, the structured workout
 * is re-derived from the updated text.
 */
public record UpdateSessionRequest(
        LocalDate date,
        @Min(0) Integer orderInDay,
        @Size(max = 200) String title,
        String detail,
        @Size(max = 30) String zone,
        @Min(0) Integer durationMin,
        @Min(0) Integer elevationM,
        @Min(0) @Max(10) Integer rpeMin,
        @Min(0) @Max(10) Integer rpeMax,
        SessionStatus status,
        String comment,
        List<WorkoutStructure.Node> workout,
        /** GYM only: link (or re-link) the session to a program variant. */
        java.util.UUID templateVariantId) {
}
