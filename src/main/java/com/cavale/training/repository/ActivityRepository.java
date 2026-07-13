package com.cavale.training.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.Activity;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findBySessionId(UUID sessionId);

    List<Activity> findBySessionIdIn(Collection<UUID> sessionIds);

    Optional<Activity> findByExternalId(Long externalId);

    List<Activity> findByExternalIdIn(Collection<Long> externalIds);

    /** The whole training history of one athlete — the hub's stats corpus. */
    List<Activity> findByUserId(UUID userId);

    /** Unattached (history) activities around a date — the matcher's candidates. */
    List<Activity> findByUserIdAndSessionIsNullAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    /** Strava activities whose best efforts haven't been extracted yet, oldest first. */
    List<Activity> findByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalseOrderByDateAsc(
            UUID userId, Limit limit);

    long countByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalse(UUID userId);

    long countByUserIdAndExternalIdIsNotNull(UUID userId);
}
