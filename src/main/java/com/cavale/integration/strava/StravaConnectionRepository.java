package com.cavale.integration.strava;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StravaConnectionRepository extends JpaRepository<StravaConnection, UUID> {

    Optional<StravaConnection> findByUserId(UUID userId);

    Optional<StravaConnection> findByAthleteId(long athleteId);
}
