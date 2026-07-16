package com.cavale.training.dto;

import com.cavale.training.domain.ShoePurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Create or fully replace a pair of shoes. */
public record ShoeRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 60, message = "Brand must not exceed 60 characters")
        String brand,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Color must be a hex value like #E4002B")
        String color,

        ShoePurpose purpose,

        @Positive(message = "Retirement distance must be positive")
        Integer retirementKm,

        Boolean retired,

        Boolean isDefault) {

    public boolean isRetired() {
        return Boolean.TRUE.equals(retired);
    }

    public boolean wantsDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
