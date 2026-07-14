package com.cavale.gym.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.gym.domain.GymTemplateVariant;

public interface GymTemplateVariantRepository extends JpaRepository<GymTemplateVariant, UUID> {

    List<GymTemplateVariant> findByTemplateIdOrderByLabelAsc(UUID templateId);

    List<GymTemplateVariant> findByTemplateIdInOrderByLabelAsc(List<UUID> templateIds);

    boolean existsByTemplateIdAndLabelIgnoreCase(UUID templateId, String label);

    long countByTemplateId(UUID templateId);
}
