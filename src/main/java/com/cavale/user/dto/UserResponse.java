package com.cavale.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.cavale.user.domain.User;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        BigDecimal weightKg,
        Integer heightCm,
        LocalDate birthDate,
        Integer maxHr,
        Integer restingHr,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getWeightKg(), user.getHeightCm(), user.getBirthDate(),
                user.getMaxHr(), user.getRestingHr(), user.getCreatedAt());
    }
}
