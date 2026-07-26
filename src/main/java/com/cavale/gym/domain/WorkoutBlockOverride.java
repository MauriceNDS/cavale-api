package com.cavale.gym.domain;

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
 * A mid-workout deviation from the plan, scoped to ONE workout: the block's
 * exercise replaced by an alternative (machine taken) and/or the block
 * skipped (no time). The template itself is never touched. A row that ends
 * up neutral — no replacement, not skipped — is pruned by the service.
 */
@Entity
@Table(name = "workout_block_override")
public class WorkoutBlockOverride extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_log_id", nullable = false, updatable = false)
    private WorkoutLog workoutLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_exercise_id", nullable = false, updatable = false)
    private TemplateExercise templateExercise;

    /** The replacement exercise — null means "as prescribed". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(nullable = false)
    private boolean skipped;

    /** Adjusted set count for this workout (0 allowed) — null means "as prescribed". */
    private Integer sets;

    /**
     * Regrouping decided on the gym floor. The key alone cannot express
     * "deliberately on its own" — that is also null — so a flag says whether
     * the athlete has spoken at all; until then the program's grouping wins.
     */
    @Column(name = "group_key", length = 4)
    private String groupKey;

    @Column(name = "group_overridden", nullable = false)
    private boolean groupOverridden;

    protected WorkoutBlockOverride() {
    }

    public WorkoutBlockOverride(WorkoutLog workoutLog, TemplateExercise templateExercise) {
        this.workoutLog = workoutLog;
        this.templateExercise = templateExercise;
    }

    public void replaceWith(Exercise exercise) {
        this.exercise = exercise;
    }

    public void skip() {
        this.skipped = true;
    }

    public void restore() {
        this.skipped = false;
    }

    public void adjustSets(Integer sets) {
        this.sets = sets;
    }

    /** Pair this block with its neighbours for this workout, or set it loose. */
    public void regroup(String groupKey) {
        this.groupKey = groupKey;
        this.groupOverridden = true;
    }

    /** Hand the grouping back to the program. */
    public void clearGrouping() {
        this.groupKey = null;
        this.groupOverridden = false;
    }

    /** The grouping in force, given what the program prescribes. */
    public String effectiveGroupKey(String prescribed) {
        return groupOverridden ? groupKey : prescribed;
    }

    /** Nothing left to override — the row has no reason to exist. */
    public boolean isNeutral() {
        return exercise == null && !skipped && sets == null && !groupOverridden;
    }

    public UUID getId() {
        return id;
    }

    public WorkoutLog getWorkoutLog() {
        return workoutLog;
    }

    public TemplateExercise getTemplateExercise() {
        return templateExercise;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public Integer getSets() {
        return sets;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public boolean isGroupOverridden() {
        return groupOverridden;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        WorkoutBlockOverride other = (WorkoutBlockOverride) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
