package com.cavale.user.domain;

/**
 * Account-access lifecycle, controlled by an admin — distinct from
 * {@link AthleteStatus} (which is training availability, e.g. injured/sick).
 *
 * <p>A brand-new account is {@code PENDING} and can reach nothing until an
 * admin {@code ACTIVE}s it. An admin may {@code DISABLE} a previously active
 * account to lock it back out. These three states are exactly the filters the
 * admin console offers: new / activated / deactivated.
 */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    DISABLED
}
