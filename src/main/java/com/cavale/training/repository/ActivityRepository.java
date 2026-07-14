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

    /** One page of the history, for the unified activities feed. */
    org.springframework.data.domain.Page<Activity> findByUserId(
            UUID userId, org.springframework.data.domain.Pageable pageable);

    /** Feed search: name (activity or linked session title) + date range. */
    @org.springframework.data.jpa.repository.Query("""
            select a from Activity a left join a.session s
            where a.userId = :userId
              and a.date between :from and :to
              and (lower(coalesce(a.name, '')) like :pattern
                   or lower(coalesce(s.title, '')) like :pattern)
            """)
    org.springframework.data.domain.Page<Activity> search(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDate to,
            org.springframework.data.domain.Pageable pageable);

    /** Unattached (history) activities around a date — the matcher's candidates. */
    List<Activity> findByUserIdAndSessionIsNullAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    /** Strava activities whose best efforts haven't been extracted yet, oldest first. */
    List<Activity> findByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalseOrderByDateAsc(
            UUID userId, Limit limit);

    long countByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalse(UUID userId);

    long countByUserIdAndExternalIdIsNotNull(UUID userId);
}
