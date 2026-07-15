package com.cavale.training.dto;

import java.math.BigDecimal;

import com.cavale.training.domain.WaypointKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Add or edit a waypoint (aid station, summit…) along a course. */
public record WaypointRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must not exceed 120 characters")
        String name,

        @NotNull(message = "Kind is required")
        WaypointKind kind,

        @NotNull(message = "Distance is required")
        @PositiveOrZero(message = "Distance must be zero or more")
        BigDecimal distanceKm,

        Integer elevationM,

        @Size(max = 300, message = "Note must not exceed 300 characters")
        String note) {
}
