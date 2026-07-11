package com.cavale.training.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Actual performance recorded against a planned session (manual or Strava). */
@Entity
@Table(name = "activity")
public class Activity extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planned_session_id", nullable = false, updatable = false, unique = true)
    private PlannedSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActivitySource source;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "elevation_m")
    private Integer elevationM;

    @Column(name = "avg_hr")
    private Integer avgHr;

    @Column(columnDefinition = "text")
    private String comment;

    /** Origin id at the source (Strava activity id) — null for manual entries. */
    @Column(name = "external_id")
    private Long externalId;

    /** Activity name at the source ("Morning Trail Run") — null for manual entries. */
    @Column(length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "perceived_effort", length = 15)
    private PerceivedEffort perceivedEffort;

    /** Downsampled Strava streams (time/distance/hr/alt/vel) as JSON, for charts. */
    @Column(name = "streams_json", columnDefinition = "text")
    private String streamsJson;

    protected Activity() {
    }

    public Activity(PlannedSession session, ActivitySource source, LocalDate date, int durationMin,
                    BigDecimal distanceKm, Integer elevationM, Integer avgHr, String comment) {
        this.session = session;
        this.userId = session.getUserId();
        this.source = source;
        this.date = date;
        this.durationMin = durationMin;
        this.distanceKm = distanceKm;
        this.elevationM = elevationM;
        this.avgHr = avgHr;
        this.comment = comment;
    }

    public static Activity fromStrava(PlannedSession session, LocalDate date, int durationMin,
                                      BigDecimal distanceKm, Integer elevationM, Integer avgHr,
                                      String name, long externalId) {
        Activity activity = new Activity(session, ActivitySource.STRAVA, date, durationMin,
                distanceKm, elevationM, avgHr, null);
        activity.name = name;
        activity.externalId = externalId;
        return activity;
    }

    public void recordFeedback(PerceivedEffort perceivedEffort, String comment) {
        this.perceivedEffort = perceivedEffort;
        this.comment = comment;
    }

    public void attachStreams(String streamsJson) {
        this.streamsJson = streamsJson;
    }

    public void updateMeasures(int durationMin, BigDecimal distanceKm, Integer elevationM,
                               Integer avgHr, String comment) {
        this.durationMin = durationMin;
        this.distanceKm = distanceKm;
        this.elevationM = elevationM;
        this.avgHr = avgHr;
        this.comment = comment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public PlannedSession getSession() {
        return session;
    }

    public ActivitySource getSource() {
        return source;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public Integer getElevationM() {
        return elevationM;
    }

    public Integer getAvgHr() {
        return avgHr;
    }

    public String getComment() {
        return comment;
    }

    public Long getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public PerceivedEffort getPerceivedEffort() {
        return perceivedEffort;
    }

    public String getStreamsJson() {
        return streamsJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Activity other = (Activity) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
