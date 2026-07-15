package com.cavale.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Upload a GPX (as text) for an objective; name defaults to the track/objective name. */
public record CourseImportRequest(

        @NotBlank(message = "The GPX content is required")
        @Size(max = 8_000_000, message = "GPX file too large")
        String gpx,

        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name) {
}
