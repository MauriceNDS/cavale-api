package com.cavale.training.domain;

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

/**
 * A pair of the athlete's shoes. Mileage isn't stored — it's summed from the
 * activities linked to it, so it stays correct through edits. A retirement
 * threshold (km) drives the "time to replace" warning.
 */
@Entity
@Table(name = "shoe")
public class Shoe extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 60)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ShoePurpose purpose;

    @Column(name = "retirement_km")
    private Integer retirementKm;

    @Column(nullable = false)
    private boolean retired;

    protected Shoe() {
    }

    public Shoe(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public void update(String name, String brand, ShoePurpose purpose, Integer retirementKm,
                       boolean retired) {
        this.name = name;
        this.brand = brand;
        this.purpose = purpose;
        this.retirementKm = retirementKm;
        this.retired = retired;
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

    public String getBrand() {
        return brand;
    }

    public ShoePurpose getPurpose() {
        return purpose;
    }

    public Integer getRetirementKm() {
        return retirementKm;
    }

    public boolean isRetired() {
        return retired;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Shoe other = (Shoe) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
