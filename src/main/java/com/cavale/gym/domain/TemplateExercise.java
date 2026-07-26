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
 * One prescription inside a variant: 3 × 6 squats @ 75 %, 3 min rest.
 *
 * <p>Consecutive prescriptions sharing a {@link #groupKey} are performed in
 * rotation — a superset, or a whole circuit when the group holds every
 * exercise of the variant. The group runs for as many rounds as its
 * longest member has sets, and {@link #restSec} keeps its plain meaning
 * throughout: the rest taken <em>after</em> this exercise. Inside a group
 * that is the short transition to the next partner; on the last member it
 * is the real rest before the next round.
 */
@Entity
@Table(name = "template_exercise")
public class TemplateExercise extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, updatable = false)
    private GymTemplateVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(nullable = false)
    private int position;

    /** Shared by the consecutive members of one superset — null when standalone. */
    @Column(name = "group_key", length = 4)
    private String groupKey;

    @Column(nullable = false)
    private int sets;

    private Integer reps;

    private Integer seconds;

    @Column(name = "rest_sec")
    private Integer restSec;

    /** % of estimated 1RM, when meaningful for the exercise. */
    @Column(name = "intensity_pct")
    private Integer intensityPct;

    @Column(length = 300)
    private String note;

    protected TemplateExercise() {
    }

    public TemplateExercise(GymTemplateVariant variant, Exercise exercise, int position,
                            int sets, Integer reps, Integer seconds, Integer restSec,
                            Integer intensityPct, String note) {
        this.variant = variant;
        this.exercise = exercise;
        this.position = position;
        this.sets = sets;
        this.reps = reps;
        this.seconds = seconds;
        this.restSec = restSec;
        this.intensityPct = intensityPct;
        this.note = note;
    }

    public void updatePrescription(int sets, Integer reps, Integer seconds, Integer restSec,
                                   Integer intensityPct, String note) {
        this.sets = sets;
        this.reps = reps;
        this.seconds = seconds;
        this.restSec = restSec;
        this.intensityPct = intensityPct;
        this.note = note;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    /** Join a superset (key shared with the neighbours) or leave it (null). */
    public void assignGroup(String groupKey) {
        this.groupKey = groupKey;
    }

    public void swapExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public UUID getId() {
        return id;
    }

    public GymTemplateVariant getVariant() {
        return variant;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public int getPosition() {
        return position;
    }

    public String getGroupKey() {
        return groupKey;
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

    public Integer getIntensityPct() {
        return intensityPct;
    }

    public String getNote() {
        return note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TemplateExercise other = (TemplateExercise) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
