package com.cavale.gym.domain;

import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A named strength program ("Force Max", "Récup course"). Its content lives
 * in variants (A/B/C) so alternating sessions stay one program.
 */
@Entity
@Table(name = "gym_template")
public class GymTemplate extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String goal;

    @Column(nullable = false)
    private boolean archived;

    protected GymTemplate() {
    }

    public GymTemplate(UUID userId, String name, String goal) {
        this.userId = userId;
        this.name = name;
        this.goal = goal;
    }

    public void update(String name, String goal, boolean archived) {
        this.name = name;
        this.goal = goal;
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

    public String getGoal() {
        return goal;
    }

    public boolean isArchived() {
        return archived;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        GymTemplate other = (GymTemplate) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
