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

    /** Highest plausible HR seen in the window — artefact spikes above 230 excluded. */
    @org.springframework.data.jpa.repository.Query("""
            select max(a.maxHr) from Activity a
            where a.userId = :userId and a.date >= :from
              and a.maxHr between 120 and 230
            """)
    Integer findObservedMaxHr(@org.springframework.data.repository.query.Param("userId") java.util.UUID userId,
                              @org.springframework.data.repository.query.Param("from") java.time.LocalDate from);


    Optional<Activity> findBySessionId(UUID sessionId);

    List<Activity> findBySessionIdIn(Collection<UUID> sessionIds);

    Optional<Activity> findByExternalId(Long externalId);

    List<Activity> findByExternalIdIn(Collection<Long> externalIds);

    /** The whole training history of one athlete — the hub's stats corpus. */
    List<Activity> findByUserId(UUID userId);

    /** One page of the history, for the unified activities feed. */
    org.springframework.data.domain.Page<Activity> findByUserId(
            UUID userId, org.springframework.data.domain.Pageable pageable);

    /** Feed search: name (activity or linked session title) + date range;
     *  runOnly excludes cross-training bikes (the RUN filter). */
    @org.springframework.data.jpa.repository.Query("""
            select a from Activity a left join a.session s
            where a.userId = :userId
              and a.date between :from and :to
              and (lower(coalesce(a.name, '')) like :pattern
                   or lower(coalesce(s.title, '')) like :pattern)
              and (:runOnly = false or a.discipline = com.cavale.training.domain.Discipline.RUN)
            """)
    org.springframework.data.domain.Page<Activity> search(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDate to,
            @org.springframework.data.repository.query.Param("runOnly") boolean runOnly,
            org.springframework.data.domain.Pageable pageable);

    /** Unattached (history) activities around a date — the matcher's candidates. */
    List<Activity> findByUserIdAndSessionIsNullAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    /** Recent runs of one discipline — the pace model's training corpus. */
    List<Activity> findByUserIdAndDisciplineAndDateGreaterThanEqual(
            UUID userId, com.cavale.training.domain.Discipline discipline, LocalDate from);

    /** Every run logged on one pair — the shoe stats corpus. */
    List<Activity> findByUserIdAndShoeIdOrderByDateAsc(UUID userId, UUID shoeId);

    /** Strava activities whose best efforts haven't been extracted yet, oldest first. */
    List<Activity> findByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalseOrderByDateAsc(
            UUID userId, Limit limit);

    long countByUserIdAndExternalIdIsNotNullAndRecordsAnalyzedFalse(UUID userId);

    long countByUserIdAndExternalIdIsNotNull(UUID userId);

    /** Accrued distance per shoe — the mileage behind each pair. */
    interface ShoeMileage {
        UUID getShoeId();

        java.math.BigDecimal getTotalKm();
    }

    @org.springframework.data.jpa.repository.Query("""
            select a.shoeId as shoeId, coalesce(sum(a.distanceKm), 0) as totalKm
            from Activity a
            where a.userId = :userId and a.shoeId is not null
            group by a.shoeId
            """)
    List<ShoeMileage> mileageByShoe(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
