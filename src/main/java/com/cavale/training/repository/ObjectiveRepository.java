package com.cavale.training.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;

public interface ObjectiveRepository extends JpaRepository<Objective, UUID> {

    List<Objective> findByPlanId(UUID planId);

    Optional<Objective> findByPlanIdAndRole(UUID planId, ObjectiveRole role);
}
