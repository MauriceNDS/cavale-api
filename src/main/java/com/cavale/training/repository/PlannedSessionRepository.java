package com.cavale.training.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.PlannedSession;

public interface PlannedSessionRepository extends JpaRepository<PlannedSession, UUID> {

    /** The calendar query: everything a user has planned in a date range. */
    List<PlannedSession> findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(
            UUID userId, LocalDate from, LocalDate to);

    List<PlannedSession> findByWeekIdOrderByDateAscOrderInDayAsc(UUID weekId);

    List<PlannedSession> findByUserId(UUID userId);
}
