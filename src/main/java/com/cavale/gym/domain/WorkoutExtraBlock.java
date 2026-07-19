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
 * An exercise added on top of the programmed variant, scoped to ONE workout
 * ("I had time for calf raises"). The template is never touched; removing
 * the block also discards its logged sets — they were part of the addition.
 */
@Entity
@Table(name = "workout_extra_block")
public class WorkoutExtraBlock extends Auditable {

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

    /** Display order among the workout's extra blocks (template blocks come first). */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int sets;

    private Integer reps;

    private Integer seconds;

    @Column(name = "rest_sec")
    private Integer restSec;

    @Column(length = 300)
    private String note;

    protected WorkoutExtraBlock() {
    }

    public WorkoutExtraBlock(WorkoutLog workoutLog, Exercise exercise, int position, int sets,
                             Integer reps, Integer seconds, Integer restSec, String note) {
        this.workoutLog = workoutLog;
        this.exercise = exercise;
        this.position = position;
        this.sets = sets;
        this.reps = reps;
        this.seconds = seconds;
        this.restSec = restSec;
        this.note = note;
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

    public int getPosition() {
        return position;
    }

    public int getSets() {
        return sets;
    }

    public Integer getReps() {
        return reps;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public Integer getRestSec() {
        return restSec;
    }

    public String getNote() {
        return note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        WorkoutExtraBlock other = (WorkoutExtraBlock) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
