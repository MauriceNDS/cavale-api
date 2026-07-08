package com.cavale.training.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.TrainingPlan;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, UUID> {

    List<TrainingPlan> findByUserIdOrderByStartDateDesc(UUID userId);
}
