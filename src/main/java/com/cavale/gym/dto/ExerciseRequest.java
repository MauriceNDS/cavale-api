package com.cavale.gym.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.Muscle;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or full-replace an exercise. derivedFromId only counts at creation
 * (a derivation is a new exercise, its parent never changes afterwards).
 */
public record ExerciseRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @NotNull(message = "Category is required")
        ExerciseCategory category,

        @NotNull(message = "Equipment is required")
        Equipment equipment,

        @NotNull(message = "Measure is required")
        ExerciseMeasure measure,

        String description,

        @Size(max = 500, message = "Resource URL must not exceed 500 characters")
        String resourceUrl,

        String runningBenefit,

        Set<Muscle> muscles,

        /** Load step the live workout moves by — null falls back to the equipment's. */
        @DecimalMin(value = "0.25", message = "An increment of at least 0.25 kg")
        @DecimalMax(value = "50", message = "An increment of at most 50 kg")
        BigDecimal incrementKg,

        /** A sane load for the very first session, before any history exists. */
        @DecimalMin(value = "0", message = "Reference weight must not be negative")
        @DecimalMax(value = "500", message = "Reference weight must be at most 500 kg")
        BigDecimal referenceWeightKg,

        UUID derivedFromId,

        Boolean archived) {
}
