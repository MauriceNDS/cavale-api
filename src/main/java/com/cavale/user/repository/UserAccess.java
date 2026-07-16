package com.cavale.user.repository;

import com.cavale.user.domain.AccountStatus;
import com.cavale.user.domain.UserRole;

/**
 * Closed projection of just the two columns the per-request access gate needs.
 * Keeps that hot-path query from loading the whole athlete row.
 */
public interface UserAccess {

    AccountStatus getAccountStatus();

    UserRole getRole();
}
