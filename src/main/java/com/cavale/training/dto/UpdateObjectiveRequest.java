package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cavale.training.domain.ObjectiveType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Full replacement (PUT semantics): the edit form always holds the whole
 * objective, so every save sends every field — null clears an optional one.
 */
public record UpdateObjectiveRequest(

        @NotNull(message = "Type is required")
        ObjectiveType type,

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        LocalDate date,

        @Positive(message = "Distance must be positive")
        BigDecimal distanceKm,

        @Positive(message = "Elevation gain must be positive")
        Integer elevationGainM,

        @Positive(message = "Target time must be positive")
        Integer targetTimeMin,

        @Positive(message = "Result time must be positive")
        Integer resultTimeMin,

        @Size(max = 150, message = "Location must not exceed 150 characters")
        String location,

        String notes) {
}
