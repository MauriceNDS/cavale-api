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

/**
 * Actual performance data. Attached to a planned session it is that session's
 * validation (manual entry or Strava import); without one it is imported
 * Strava history — the stats corpus behind the athlete hub.
 */
@Entity
@Table(name = "activity")
public class Activity extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_session_id", unique = true)
    private PlannedSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActivitySource source;

    /** RUN or CROSS (bike): cross-training feeds load but not run-only stats. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Discipline discipline = Discipline.RUN;

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

    /** Strides per minute, both legs (Strava reports per leg — stored doubled). */
    @Column(name = "avg_cadence_spm", precision = 5, scale = 1)
    private BigDecimal avgCadenceSpm;

    /** Strava's relative effort (suffer score) — training load per activity. */
    @Column(name = "relative_effort")
    private Integer relativeEffort;

    @Column(name = "max_hr")
    private Integer maxHr;

    /** True once Strava's per-activity best efforts have been extracted. */
    @Column(name = "records_analyzed", nullable = false)
    private boolean recordsAnalyzed;

    /** Pain/niggle reported at validation — the injury early-warning trail. */
    @Column(name = "pain_flag", nullable = false)
    private boolean painFlag;

    /** The shoe worn (asked at validation) — accrues that shoe's mileage. */
    @Column(name = "shoe_id")
    private UUID shoeId;

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

    /** Imported Strava history, not (yet) attached to any planned session. */
    public static Activity stravaHistory(UUID userId, LocalDate date, int durationMin,
                                         BigDecimal distanceKm, Integer elevationM, Integer avgHr,
                                         String name, long externalId) {
        Activity activity = new Activity();
        activity.userId = userId;
        activity.source = ActivitySource.STRAVA;
        activity.date = date;
        activity.durationMin = durationMin;
        activity.distanceKm = distanceKm;
        activity.elevationM = elevationM;
        activity.avgHr = avgHr;
        activity.name = name;
        activity.externalId = externalId;
        return activity;
    }

    /** Adopt a history activity as a session's validation. */
    public void attachToSession(PlannedSession session) {
        if (this.session != null) {
            throw new IllegalStateException("Activity is already attached to a session");
        }
        if (!session.getUserId().equals(this.userId)) {
            throw new IllegalArgumentException("Session belongs to another user");
        }
        this.session = session;
    }

    /** Fill hub metrics; never erases a value already present. */
    public void enrich(BigDecimal avgCadenceSpm, Integer relativeEffort, Integer maxHr) {
        if (this.avgCadenceSpm == null) {
            this.avgCadenceSpm = avgCadenceSpm;
        }
        if (this.relativeEffort == null) {
            this.relativeEffort = relativeEffort;
        }
        if (this.maxHr == null) {
            this.maxHr = maxHr;
        }
    }

    public void markRecordsAnalyzed() {
        this.recordsAnalyzed = true;
    }

    /** Tag this activity's discipline (RUN or CROSS) — set from its session. */
    public void markDiscipline(Discipline discipline) {
        this.discipline = discipline;
    }

    /** Cross-training (a bike) is excluded from every run-only statistic. */
    public boolean isRun() {
        return discipline == Discipline.RUN;
    }

    /** Link the shoe worn on this activity (or clear it with null). */
    public void assignShoe(UUID shoeId) {
        this.shoeId = shoeId;
    }

    public void recordFeedback(PerceivedEffort perceivedEffort, String comment, boolean painFlag) {
        this.perceivedEffort = perceivedEffort;
        this.comment = comment;
        this.painFlag = painFlag;
    }

    public void attachStreams(String streamsJson) {
        this.streamsJson = streamsJson;
    }

    /** Refresh the source-of-truth measures from Strava; athlete feedback stays. */
    public void refreshFromSource(String name, int durationMin, BigDecimal distanceKm,
                                  Integer elevationM, Integer avgHr) {
        this.name = name;
        this.durationMin = durationMin;
        this.distanceKm = distanceKm;
        this.elevationM = elevationM;
        this.avgHr = avgHr;
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

    public Discipline getDiscipline() {
        return discipline;
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

    public BigDecimal getAvgCadenceSpm() {
        return avgCadenceSpm;
    }

    public Integer getRelativeEffort() {
        return relativeEffort;
    }

    public Integer getMaxHr() {
        return maxHr;
    }

    public boolean isRecordsAnalyzed() {
        return recordsAnalyzed;
    }

    public boolean isPainFlag() {
        return painFlag;
    }

    public UUID getShoeId() {
        return shoeId;
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
