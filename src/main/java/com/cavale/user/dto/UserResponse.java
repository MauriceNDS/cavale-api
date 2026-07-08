package com.cavale.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.cavale.user.domain.User;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
