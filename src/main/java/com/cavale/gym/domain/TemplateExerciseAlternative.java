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

/** Fallback when the machine is busy or there's no gym — in preference order. */
@Entity
@Table(name = "template_exercise_alternative")
public class TemplateExerciseAlternative extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_exercise_id", nullable = false, updatable = false)
    private TemplateExercise templateExercise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false, updatable = false)
    private Exercise exercise;

    @Column(nullable = false)
    private int position;

    protected TemplateExerciseAlternative() {
    }

    public TemplateExerciseAlternative(TemplateExercise templateExercise, Exercise exercise,
                                       int position) {
        this.templateExercise = templateExercise;
        this.exercise = exercise;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public TemplateExercise getTemplateExercise() {
        return templateExercise;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TemplateExerciseAlternative other = (TemplateExerciseAlternative) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
