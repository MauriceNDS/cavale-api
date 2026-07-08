package com.cavale.training.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_week")
public class PlanWeek extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private TrainingPlan plan;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(length = 100)
    private String phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_type", nullable = false, length = 20)
    private WeekType weekType;

    @Column(name = "target_volume_km", precision = 5, scale = 1)
    private BigDecimal targetVolumeKm;

    @Column(name = "target_elevation_m")
    private Integer targetElevationM;

    @Column(name = "target_load_ua")
    private Integer targetLoadUa;

    @Column(columnDefinition = "text")
    private String focus;

    protected PlanWeek() {
    }

    public PlanWeek(TrainingPlan plan, int weekNumber, LocalDate startDate, String phase, WeekType weekType,
                    BigDecimal targetVolumeKm, Integer targetElevationM, Integer targetLoadUa, String focus) {
        this.plan = plan;
        this.weekNumber = weekNumber;
        this.startDate = startDate;
        this.phase = phase;
        this.weekType = weekType;
        this.targetVolumeKm = targetVolumeKm;
        this.targetElevationM = targetElevationM;
        this.targetLoadUa = targetLoadUa;
        this.focus = focus;
    }

    public UUID getId() {
        return id;
    }

    public TrainingPlan getPlan() {
        return plan;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getPhase() {
        return phase;
    }

    public WeekType getWeekType() {
        return weekType;
    }

    public BigDecimal getTargetVolumeKm() {
        return targetVolumeKm;
    }

    public Integer getTargetElevationM() {
        return targetElevationM;
    }

    public Integer getTargetLoadUa() {
        return targetLoadUa;
    }

    public String getFocus() {
        return focus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PlanWeek other = (PlanWeek) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
