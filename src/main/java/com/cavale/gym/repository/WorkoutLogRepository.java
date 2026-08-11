package com.cavale.gym.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;

public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, UUID> {

    /** At most one workout runs at a time — the resume banner's query. */
    Optional<WorkoutLog> findFirstByUserIdAndStatusOrderByStartedAtDesc(UUID userId, WorkoutStatus status);

    List<WorkoutLog> findByUserIdAndStatusOrderByStartedAtDesc(UUID userId, WorkoutStatus status);

    org.springframework.data.domain.Page<WorkoutLog> findByUserIdAndStatus(
            UUID userId, WorkoutStatus status, org.springframework.data.domain.Pageable pageable);

    /** Feed search: template name + started-at range. */
    @org.springframework.data.jpa.repository.Query("""
            select w from WorkoutLog w
            where w.userId = :userId
              and w.status = :status
              and w.startedAt >= :from and w.startedAt < :to
              and lower(coalesce(w.templateName, 'renfo')) like :pattern
            """)
    org.springframework.data.domain.Page<WorkoutLog> search(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("status") WorkoutStatus status,
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to,
            org.springframework.data.domain.Pageable pageable);

    Optional<WorkoutLog> findBySessionId(UUID sessionId);

    List<WorkoutLog> findBySessionIdIn(List<UUID> sessionIds);
}
