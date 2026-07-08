package com.cavale.training.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.PlanWeek;

public interface PlanWeekRepository extends JpaRepository<PlanWeek, UUID> {

    List<PlanWeek> findByPlanIdOrderByWeekNumber(UUID planId);
}
