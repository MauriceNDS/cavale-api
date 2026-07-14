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

    Optional<WorkoutLog> findBySessionId(UUID sessionId);
}
