package com.cavale.user.domain;

/**
 * Authorization role. {@code ADMIN} may list every account and
 * activate/deactivate them; {@code USER} is a regular athlete.
 */
public enum UserRole {
    USER,
    ADMIN
}
