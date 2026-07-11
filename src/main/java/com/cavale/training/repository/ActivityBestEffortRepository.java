package com.cavale.training.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.ActivityBestEffort;

public interface ActivityBestEffortRepository extends JpaRepository<ActivityBestEffort, UUID> {

    List<ActivityBestEffort> findByUserId(UUID userId);
}
