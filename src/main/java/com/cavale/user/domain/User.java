package com.cavale.user.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /* Athlete profile — all optional, filled in over time. */

    @Column(name = "weight_kg", precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "max_hr")
    private Integer maxHr;

    @Column(name = "resting_hr")
    private Integer restingHr;

    protected User() {}

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    /** Full replacement of the athlete profile (the form always sends everything). */
    public void updateProfile(String displayName, BigDecimal weightKg, Integer heightCm,
                              LocalDate birthDate, Integer maxHr, Integer restingHr) {
        this.displayName = displayName;
        this.weightKg = weightKg;
        this.heightCm = heightCm;
        this.birthDate = birthDate;
        this.maxHr = maxHr;
        this.restingHr = restingHr;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Integer getMaxHr() {
        return maxHr;
    }

    public Integer getRestingHr() {
        return restingHr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;

        User other = (User) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
