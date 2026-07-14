package com.cavale.gym.dto;

import java.util.Set;
import java.util.UUID;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.Muscle;

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

        UUID derivedFromId,

        Boolean archived) {
}
