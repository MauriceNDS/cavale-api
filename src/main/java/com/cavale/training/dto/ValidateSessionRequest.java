package com.cavale.training.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Manual validation of a running session: time + distance required, the rest optional. */
public record ValidateSessionRequest(

        @NotNull(message = "Duration is required")
        @Min(value = 1, message = "Duration must be at least 1 minute")
        Integer durationMin,

        @NotNull(message = "Distance is required")
        @DecimalMin(value = "0.01", message = "Distance must be positive")
        BigDecimal distanceKm,

        @Min(0) Integer elevationM,

        @Min(30) @Max(250) Integer avgHr,

        com.cavale.training.domain.PerceivedEffort perceivedEffort,

        /** Pain or niggle felt during the run — feeds the injury early-warning trail. */
        Boolean painFlag,

        /** The shoe worn — asked at validation, accrues that pair's mileage. */
        java.util.UUID shoeId,

        @Size(max = 2000, message = "Comment must not exceed 2000 characters")
        String comment) {

    public boolean pain() {
        return Boolean.TRUE.equals(painFlag);
    }
}
