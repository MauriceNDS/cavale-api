package com.cavale.training.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A fastest split inside one activity (Strava "best effort"): the quickest
 * 1 km, 5 km, 10 km… that activity contains. The athlete's distance records
 * are the minimum elapsed time per distance across all of these.
 */
@Entity
@Table(name = "activity_best_effort")
public class ActivityBestEffort extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false, updatable = false)
    private Activity activity;

    /** Denormalized for record queries without joins. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Strava's label ("5k", "Half-Marathon"…). */
    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "distance_m", nullable = false)
    private int distanceM;

    @Column(name = "elapsed_sec", nullable = false)
    private int elapsedSec;

    /** Denormalized activity date — "record set on …". */
    @Column(nullable = false)
    private LocalDate date;

    protected ActivityBestEffort() {
    }

    public ActivityBestEffort(Activity activity, String name, int distanceM, int elapsedSec) {
        this.activity = activity;
        this.userId = activity.getUserId();
        this.name = name;
        this.distanceM = distanceM;
        this.elapsedSec = elapsedSec;
        this.date = activity.getDate();
    }

    public UUID getId() {
        return id;
    }

    public Activity getActivity() {
        return activity;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public int getDistanceM() {
        return distanceM;
    }

    public int getElapsedSec() {
        return elapsedSec;
    }

    public LocalDate getDate() {
        return date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ActivityBestEffort other = (ActivityBestEffort) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
