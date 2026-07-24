package com.cavale.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.cavale.user.domain.PersonalToken;

/** One row of the settings token list — never contains the token itself. */
public record PersonalTokenResponse(
        UUID id,
        String label,
        Instant issuedAt,
        Instant expiresAt,
        boolean revoked) {

    public static PersonalTokenResponse from(PersonalToken token) {
        return new PersonalTokenResponse(token.getId(), token.getLabel(),
                token.getIssuedAt(), token.getExpiresAt(), token.isRevoked());
    }
}
