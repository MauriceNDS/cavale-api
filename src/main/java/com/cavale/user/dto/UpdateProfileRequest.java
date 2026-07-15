package com.cavale.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the athlete profile (the form always sends every
 * field — null clears an optional one).
 */
public record UpdateProfileRequest(

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName,

        @Positive(message = "Weight must be positive")
        BigDecimal weightKg,

        @Positive(message = "Height must be positive")
        Integer heightCm,

        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @Positive(message = "Max HR must be positive")
        Integer maxHr,

        @Positive(message = "Resting HR must be positive")
        Integer restingHr,

        /** null = unchanged; false hides the strength side in the UI. */
        Boolean gymEnabled,

        /** null = unchanged; UI and coach-generated content language. */
        @Pattern(regexp = "fr|en", message = "Language must be 'fr' or 'en'")
        String preferredLanguage) {
}
