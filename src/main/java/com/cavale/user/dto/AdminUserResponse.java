package com.cavale.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.cavale.user.domain.AccountStatus;
import com.cavale.user.domain.User;
import com.cavale.user.domain.UserRole;

/**
 * What the admin console shows for each account. Deliberately lean — the
 * athlete's body metrics (weight, HR…) never leak into the admin list.
 */
public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        AccountStatus accountStatus,
        UserRole role,
        Instant createdAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getAccountStatus(), user.getRole(), user.getCreatedAt());
    }
}
