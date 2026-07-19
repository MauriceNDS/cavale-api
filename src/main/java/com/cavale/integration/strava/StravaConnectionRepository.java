package com.cavale.integration.strava;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface StravaConnectionRepository extends JpaRepository<StravaConnection, UUID> {

    Optional<StravaConnection> findByUserId(UUID userId);

    Optional<StravaConnection> findByAthleteId(long athleteId);

    /**
     * Same lookup, taking a write lock on the row. Used when refreshing the
     * OAuth token so concurrent refreshers (webhook worker, scheduler, a user
     * request) serialize: the later one blocks until the first commits, then
     * reads the already-rotated token instead of re-spending a single-use
     * refresh token and breaking the connection.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StravaConnection c where c.userId = :userId")
    Optional<StravaConnection> findByUserIdForUpdate(UUID userId);
}
