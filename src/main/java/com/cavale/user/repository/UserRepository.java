package com.cavale.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.user.domain.AccountStatus;
import com.cavale.user.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Hot path: the access gate checks this on every authenticated request. */
    Optional<UserAccess> findAccessById(UUID id);

    /** Admin console: newest accounts first so fresh sign-ups surface on top. */
    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findByAccountStatusOrderByCreatedAtDesc(AccountStatus accountStatus);

    /** Bootstrap: how many real accounts exist (demo sandboxes don't count). */
    long countByDemoFalse();

    /* ── Demo sandbox ──────────────────────────────────────────────────── */

    long countByDemoTrue();

    /** Reaper: demo accounts past their TTL (deleting one cascades all its data). */
    List<User> findByDemoTrueAndCreatedAtBefore(Instant cutoff);
}
