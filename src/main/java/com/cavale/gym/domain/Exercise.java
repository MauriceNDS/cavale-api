package com.cavale.gym.domain;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One entry of the athlete's exercise library — and its theory content: how
 * to perform it, a video/article resource, the muscles it targets and why it
 * matters for trail running. An exercise performed differently (slow
 * eccentric, explosive…) is a new exercise DERIVED from its parent.
 */
@Entity
@Table(name = "exercise")
public class Exercise extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ExerciseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExerciseMeasure measure;

    /** How to perform it. */
    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "resource_url", length = 500)
    private String resourceUrl;

    /** Why a trail runner needs it. */
    @Column(name = "running_benefit", columnDefinition = "text")
    private String runningBenefit;

    /**
     * Smallest load step that is actually loadable on this exercise — the
     * granularity the live workout's −/+ buttons move by, and the value
     * suggestions are rounded down to. Null falls back to the equipment's
     * default (see {@link Equipment#defaultIncrementKg()}).
     */
    @Column(name = "increment_kg", precision = 4, scale = 2)
    private BigDecimal incrementKg;

    /**
     * A sane starting load, for the first session on this exercise — before
     * any history exists there is nothing to estimate a 1RM from.
     */
    @Column(name = "reference_weight_kg", precision = 5, scale = 1)
    private BigDecimal referenceWeightKg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "derived_from_id")
    private Exercise derivedFrom;

    @Column(nullable = false)
    private boolean archived;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercise_muscle", joinColumns = @JoinColumn(name = "exercise_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "muscle", length = 15, nullable = false)
    private Set<Muscle> muscles = new LinkedHashSet<>();

    protected Exercise() {
    }

    public Exercise(UUID userId, String name, ExerciseCategory category, Equipment equipment,
                    ExerciseMeasure measure) {
        this.userId = userId;
        this.name = name;
        this.category = category;
        this.equipment = equipment;
        this.measure = measure;
    }

    public void updateBasics(String name, ExerciseCategory category, Equipment equipment,
                             ExerciseMeasure measure) {
        this.name = name;
        this.category = category;
        this.equipment = equipment;
        this.measure = measure;
    }

    public void updateTheory(String description, String resourceUrl, String runningBenefit) {
        this.description = description;
        this.resourceUrl = resourceUrl;
        this.runningBenefit = runningBenefit;
    }

    /** How the live workout loads this exercise: step size and a cold-start weight. */
    public void updateLoading(BigDecimal incrementKg, BigDecimal referenceWeightKg) {
        this.incrementKg = incrementKg;
        this.referenceWeightKg = referenceWeightKg;
    }

    /** The step to move by — the exercise's own, else the equipment's default. */
    public BigDecimal effectiveIncrementKg() {
        return incrementKg != null ? incrementKg : equipment.defaultIncrementKg();
    }

    public void updateMuscles(Set<Muscle> muscles) {
        this.muscles.clear();
        if (muscles != null) {
            this.muscles.addAll(muscles);
        }
    }

    public void deriveFrom(Exercise parent) {
        this.derivedFrom = parent;
    }

    public void updateArchived(boolean archived) {
        this.archived = archived;
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

    public ExerciseCategory getCategory() {
        return category;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public ExerciseMeasure getMeasure() {
        return measure;
    }

    public String getDescription() {
        return description;
    }

    public String getResourceUrl() {
        return resourceUrl;
    }

    public String getRunningBenefit() {
        return runningBenefit;
    }

    public BigDecimal getIncrementKg() {
        return incrementKg;
    }

    public BigDecimal getReferenceWeightKg() {
        return referenceWeightKg;
    }

    public Exercise getDerivedFrom() {
        return derivedFrom;
    }

    public boolean isArchived() {
        return archived;
    }

    public Set<Muscle> getMuscles() {
        return muscles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Exercise other = (Exercise) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
