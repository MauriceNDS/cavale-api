package com.cavale.training.dto;

import java.time.LocalDate;

import com.cavale.training.domain.SessionStatus;

import jakarta.validation.constraints.Min;

/** Partial update: only non-null fields are applied. */
public record UpdateSessionRequest(
        LocalDate date,
        @Min(0) Integer orderInDay,
        SessionStatus status,
        String comment) {
}
