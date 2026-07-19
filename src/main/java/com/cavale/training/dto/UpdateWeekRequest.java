package com.cavale.training.dto;

import java.math.BigDecimal;

import com.cavale.training.domain.WeekType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update: only non-null fields are applied. A blank {@code phase}
 * or {@code focus} clears the stored value; targets can only be replaced.
 */
public record UpdateWeekRequest(
        @Size(max = 100) String phase,
        WeekType weekType,
        @Min(0) BigDecimal targetVolumeKm,
        @Min(0) Integer targetElevationM,
        @Min(0) Integer targetLoadUa,
        String focus) {
}
