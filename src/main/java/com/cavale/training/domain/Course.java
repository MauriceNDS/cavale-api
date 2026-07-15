package com.cavale.training.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A race course parsed from a GPX file, attached to one objective. The
 * downsampled elevation profile (cumulative [distance_m, elevation_m] pairs)
 * is stored so pacing never re-parses the file; splits and arrivals are
 * computed on read from the athlete's own sec/km-effort.
 */
@Entity
@Table(name = "course")
public class Course extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "objective_id", nullable = false, updatable = false, unique = true)
    private UUID objectiveId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "distance_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "elevation_gain_m", nullable = false)
    private int elevationGainM;

    @Column(name = "elevation_loss_m", nullable = false)
    private int elevationLossM;

    @Column(name = "profile_json", nullable = false, columnDefinition = "text")
    private String profileJson;

    protected Course() {
    }

    public Course(UUID userId, UUID objectiveId, String name, BigDecimal distanceKm,
                  int elevationGainM, int elevationLossM, String profileJson) {
        this.userId = userId;
        this.objectiveId = objectiveId;
        this.name = name;
        this.distanceKm = distanceKm;
        this.elevationGainM = elevationGainM;
        this.elevationLossM = elevationLossM;
        this.profileJson = profileJson;
    }

    /** Replace the track when a new GPX is uploaded for the same objective. */
    public void replaceTrack(String name, BigDecimal distanceKm, int elevationGainM,
                             int elevationLossM, String profileJson) {
        this.name = name;
        this.distanceKm = distanceKm;
        this.elevationGainM = elevationGainM;
        this.elevationLossM = elevationLossM;
        this.profileJson = profileJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getObjectiveId() {
        return objectiveId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public int getElevationGainM() {
        return elevationGainM;
    }

    public int getElevationLossM() {
        return elevationLossM;
    }

    public String getProfileJson() {
        return profileJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Course other = (Course) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
