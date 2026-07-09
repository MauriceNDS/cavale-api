package com.cavale.integration.strava;

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
@Table(name = "strava_connection")
public class StravaConnection extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "athlete_id", nullable = false)
    private long athleteId;

    @Column(name = "access_token", nullable = false, length = 100)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, length = 100)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(length = 100)
    private String scope;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    protected StravaConnection() {
    }

    public StravaConnection(UUID userId, long athleteId, String accessToken, String refreshToken,
                            Instant expiresAt, String scope) {
        this.userId = userId;
        this.athleteId = athleteId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.scope = scope;
    }

    public void updateTokens(String accessToken, String refreshToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    public void markSynced(Instant at) {
        this.lastSyncAt = at;
    }

    public boolean tokenExpiringSoon() {
        return expiresAt.isBefore(Instant.now().plusSeconds(300));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getAthleteId() {
        return athleteId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getScope() {
        return scope;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        StravaConnection other = (StravaConnection) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
