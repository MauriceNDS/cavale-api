package com.cavale.coach.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.coach.domain.WeeklyInsight;

public interface WeeklyInsightRepository extends JpaRepository<WeeklyInsight, UUID> {

    Optional<WeeklyInsight> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

    List<WeeklyInsight> findByUserIdOrderByWeekStartDesc(UUID userId, Limit limit);
}
