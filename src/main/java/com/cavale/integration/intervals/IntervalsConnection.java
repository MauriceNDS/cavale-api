package com.cavale.integration.intervals;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "intervals_connection")
public class IntervalsConnection extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Intervals.icu athlete id, e.g. "i647048". */
    @Column(name = "athlete_id", nullable = false, length = 20)
    private String athleteId;

    @Column(name = "api_key", nullable = false, length = 100)
    private String apiKey;

    @Column(name = "last_push_at")
    private Instant lastPushAt;

    protected IntervalsConnection() {
    }

    public IntervalsConnection(UUID userId, String athleteId, String apiKey) {
        this.userId = userId;
        this.athleteId = athleteId;
        this.apiKey = apiKey;
    }

    public void updateKey(String athleteId, String apiKey) {
        this.athleteId = athleteId;
        this.apiKey = apiKey;
    }

    public void markPushed(Instant at) {
        this.lastPushAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAthleteId() {
        return athleteId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Instant getLastPushAt() {
        return lastPushAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        IntervalsConnection other = (IntervalsConnection) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
