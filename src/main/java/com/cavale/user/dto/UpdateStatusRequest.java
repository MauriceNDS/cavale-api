package com.cavale.user.dto;

import com.cavale.user.domain.AthleteStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sets the athlete's availability; the since-date is stamped server-side. */
public record UpdateStatusRequest(

        @NotNull(message = "Status is required")
        AthleteStatus status,

        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note) {
}
