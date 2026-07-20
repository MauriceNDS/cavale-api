package com.cavale.integration.intervals;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntervalsConnectionRepository extends JpaRepository<IntervalsConnection, UUID> {

    Optional<IntervalsConnection> findByUserId(UUID userId);
}
