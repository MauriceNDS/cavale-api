package com.cavale.training.domain;

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
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "training_plan")
public class TrainingPlan extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Desired running sessions per week — scaffold input, null = default. */
    @Column(name = "runs_per_week")
    private Integer runsPerWeek;

    /** Desired strength sessions per week — scaffold input, null = default. */
    @Column(name = "gym_per_week")
    private Integer gymPerWeek;

    /** What this block optimizes for (mostly non-race seasons), null = MAINTAIN. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PlanFocus focus;

    protected TrainingPlan() {
    }

    public TrainingPlan(UUID userId, String name, String goal, LocalDate startDate, LocalDate endDate) {
        this.userId = userId;
        this.name = name;
        this.goal = goal;
        this.status = PlanStatus.ACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getGoal() {
        return goal;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Integer getRunsPerWeek() {
        return runsPerWeek;
    }

    public Integer getGymPerWeek() {
        return gymPerWeek;
    }

    public PlanFocus getFocus() {
        return focus;
    }

    public void updatePreferences(Integer runsPerWeek, Integer gymPerWeek, PlanFocus focus) {
        this.runsPerWeek = runsPerWeek;
        this.gymPerWeek = gymPerWeek;
        this.focus = focus;
    }

    public void updateStatus(PlanStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TrainingPlan other = (TrainingPlan) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
