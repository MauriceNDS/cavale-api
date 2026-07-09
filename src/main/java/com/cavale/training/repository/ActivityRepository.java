package com.cavale.training.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.Activity;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findBySessionId(UUID sessionId);

    List<Activity> findBySessionIdIn(Collection<UUID> sessionIds);

    Optional<Activity> findByExternalId(Long externalId);
}
