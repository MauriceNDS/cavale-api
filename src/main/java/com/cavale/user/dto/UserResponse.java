package com.cavale.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.cavale.user.domain.AccountStatus;
import com.cavale.user.domain.AthleteStatus;
import com.cavale.user.domain.User;
import com.cavale.user.domain.UserRole;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        BigDecimal weightKg,
        Integer heightCm,
        LocalDate birthDate,
        Integer maxHr,
        Integer restingHr,
        Integer lthr,
        boolean gymEnabled,
        String preferredLanguage,
        AthleteStatus athleteStatus,
        String statusNote,
        LocalDate statusSince,
        AccountStatus accountStatus,
        UserRole role,
        boolean demo,
        boolean hasCredentials,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getWeightKg(), user.getHeightCm(), user.getBirthDate(),
                user.getMaxHr(), user.getRestingHr(), user.getLthr(), user.isGymEnabled(),
                user.getPreferredLanguage(), user.getAthleteStatus(),
                user.getStatusNote(), user.getStatusSince(),
                user.getAccountStatus(), user.getRole(), user.isDemo(),
                user.hasRealCredentials(), user.getCreatedAt());
    }
}
