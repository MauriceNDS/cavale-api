package com.cavale.gym.domain;

import java.math.BigDecimal;
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
 * One performed set: "set 2 of squats — 6 reps at 85 kg". Values are
 * snapshots of what actually happened; editing templates never rewrites
 * history. The exercise name is snapshotted too.
 */
@Entity
@Table(name = "set_log")
public class SetLog extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_log_id", nullable = false, updatable = false)
    private WorkoutLog workoutLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false, updatable = false)
    private Exercise exercise;

    @Column(name = "exercise_name", nullable = false, length = 150)
    private String exerciseName;

    /** Block order inside the workout. */
    @Column(nullable = false)
    private int position;

    /** 1-based inside the exercise. */
    @Column(name = "set_number", nullable = false)
    private int setNumber;

    private Integer reps;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    private Integer seconds;

    /**
     * An approach set on the way to the working load. Excluded from every
     * statistic — tonnage, records, 1RM estimates, muscle balance — so that
     * logging warm-ups honestly never distorts the numbers.
     */
    @Column(nullable = false)
    private boolean warmup;

    /**
     * Reps left in reserve at the end of the set, 0–4 (4 meaning "3 or
     * more"). Optional: it is asked during the rest countdown and skipping
     * costs nothing. When present it says how close to failure the set
     * really was, which is what makes an estimated 1RM trustworthy.
     */
    private Integer rir;

    protected SetLog() {
    }

    public SetLog(WorkoutLog workoutLog, Exercise exercise, int position, int setNumber,
                  Integer reps, BigDecimal weightKg, Integer seconds) {
        this.workoutLog = workoutLog;
        this.exercise = exercise;
        this.exerciseName = exercise.getName();
        this.position = position;
        this.setNumber = setNumber;
        this.reps = reps;
        this.weightKg = weightKg;
        this.seconds = seconds;
    }

    /** Autosave correction: re-ticking a set replaces its measures. */
    public void updateMeasures(Integer reps, BigDecimal weightKg, Integer seconds, int position,
                               boolean warmup) {
        this.reps = reps;
        this.weightKg = weightKg;
        this.seconds = seconds;
        this.position = position;
        this.warmup = warmup;
    }

    /** Answered during the rest that follows the set — or never. */
    public void rateReserve(Integer rir) {
        this.rir = rir;
    }

    public void markWarmup(boolean warmup) {
        this.warmup = warmup;
    }

    public UUID getId() {
        return id;
    }

    public WorkoutLog getWorkoutLog() {
        return workoutLog;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public int getPosition() {
        return position;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public Integer getReps() {
        return reps;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public boolean isWarmup() {
        return warmup;
    }

    public Integer getRir() {
        return rir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SetLog other = (SetLog) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
