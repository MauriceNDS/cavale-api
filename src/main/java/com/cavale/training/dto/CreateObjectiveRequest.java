package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Adds a SECONDARY objective — the MAIN one is created with the plan. */
public record CreateObjectiveRequest(

        @NotNull(message = "Type is required")
        ObjectiveType type,

        /** Road or trail; defaults to trail when omitted. */
        ObjectiveKind kind,

        /** Balance or performance; defaults to balance when omitted. */
        ObjectiveIntensity intensity,

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

        @Size(max = 150, message = "Location must not exceed 150 characters")
        String location,

        String notes) {
}
