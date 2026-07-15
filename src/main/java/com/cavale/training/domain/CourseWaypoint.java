package com.cavale.training.domain;

import java.math.BigDecimal;
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

/** A point along a course (aid station, summit…) at a given distance in. */
@Entity
@Table(name = "course_waypoint")
public class CourseWaypoint extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private WaypointKind kind;

    @Column(name = "distance_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "elevation_m")
    private Integer elevationM;

    @Column(length = 300)
    private String note;

    protected CourseWaypoint() {
    }

    public CourseWaypoint(UUID courseId, String name, WaypointKind kind, BigDecimal distanceKm,
                          Integer elevationM, String note) {
        this.courseId = courseId;
        this.name = name;
        this.kind = kind;
        this.distanceKm = distanceKm;
        this.elevationM = elevationM;
        this.note = note;
    }

    public void update(String name, WaypointKind kind, BigDecimal distanceKm, Integer elevationM,
                       String note) {
        this.name = name;
        this.kind = kind;
        this.distanceKm = distanceKm;
        this.elevationM = elevationM;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public WaypointKind getKind() {
        return kind;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public Integer getElevationM() {
        return elevationM;
    }

    public String getNote() {
        return note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CourseWaypoint other = (CourseWaypoint) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
