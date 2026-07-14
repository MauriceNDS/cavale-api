package com.cavale.gym.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.GymTemplate;

public interface GymTemplateRepository extends JpaRepository<GymTemplate, UUID> {

    List<GymTemplate> findByUserIdOrderByNameAsc(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
}
