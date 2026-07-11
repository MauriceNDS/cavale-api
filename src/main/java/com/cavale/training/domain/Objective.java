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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * What a plan is built around. Every plan has exactly one MAIN objective
 * (a race, injury recovery, getting back in shape…) — its period is the
 * plan's date range — plus any number of SECONDARY objectives (intermediate
 * races the program accounts for).
 */
@Entity
@Table(name = "objective")
public class Objective extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private TrainingPlan plan;

    /** Denormalized from the plan owner: ownership checks without a join. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ObjectiveRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ObjectiveType type;

    @Column(nullable = false, length = 150)
    private String name;

    /** Event date (race day). Null for open-ended goals — the plan end is the horizon. */
    @Column
    private LocalDate date;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "elevation_gain_m")
    private Integer elevationGainM;

    @Column(name = "target_time_min")
    private Integer targetTimeMin;

    /** Actual finish time once the objective has been raced/reached. */
    @Column(name = "result_time_min")
    private Integer resultTimeMin;

    @Column(length = 150)
    private String location;

    @Column(columnDefinition = "text")
    private String notes;

    protected Objective() {
    }

    public Objective(TrainingPlan plan, ObjectiveRole role, ObjectiveType type, String name, LocalDate date) {
        this.plan = plan;
        this.userId = plan.getUserId();
        this.role = role;
        this.type = type;
        this.name = name;
        this.date = date;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void updateType(ObjectiveType type) {
        this.type = type;
    }

    public void updateDate(LocalDate date) {
        this.date = date;
    }

    public void updateRaceProfile(BigDecimal distanceKm, Integer elevationGainM, String location) {
        this.distanceKm = distanceKm;
        this.elevationGainM = elevationGainM;
        this.location = location;
    }

    public void updateTargetTimeMin(Integer targetTimeMin) {
        this.targetTimeMin = targetTimeMin;
    }

    public void recordResult(Integer resultTimeMin) {
        this.resultTimeMin = resultTimeMin;
    }

    public void updateNotes(String notes) {
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public TrainingPlan getPlan() {
        return plan;
    }

    public UUID getUserId() {
        return userId;
    }

    public ObjectiveRole getRole() {
        return role;
    }

    public ObjectiveType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public Integer getElevationGainM() {
        return elevationGainM;
    }

    public Integer getTargetTimeMin() {
        return targetTimeMin;
    }

    public Integer getResultTimeMin() {
        return resultTimeMin;
    }

    public String getLocation() {
        return location;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Objective other = (Objective) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
