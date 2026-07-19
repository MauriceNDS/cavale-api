package com.cavale.training.domain;

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
@Table(name = "planned_session")
public class PlannedSession extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id", nullable = false)
    private PlanWeek week;

    /** Denormalized from the plan owner: keeps the calendar query join-free. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "order_in_day", nullable = false)
    private int orderInDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Discipline discipline;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(length = 30)
    private String zone;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "elevation_m")
    private Integer elevationM;

    @Column(name = "rpe_min")
    private Integer rpeMin;

    @Column(name = "rpe_max")
    private Integer rpeMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionStatus status;

    /** Athlete's free-text note — the detail carries the coach's structure. */
    @Column(columnDefinition = "text")
    private String comment;

    /** Canonical workout tree (allure blocks + loops) as JSON — see WorkoutStructure. */
    @Column(name = "workout_json", columnDefinition = "text")
    private String workoutJson;

    /**
     * The strength program this GYM session realizes ("Force Max · A").
     * EAGER on purpose: SessionResponse is mapped outside the transaction
     * (OSIV off), and the variant+template pair is two tiny rows.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "template_variant_id")
    private com.cavale.gym.domain.GymTemplateVariant templateVariant;

    protected PlannedSession() {
    }

    public PlannedSession(PlanWeek week, UUID userId, LocalDate date, int orderInDay, Discipline discipline,
                          String title, String detail, String zone, Integer durationMin, Integer elevationM,
                          Integer rpeMin, Integer rpeMax) {
        this.week = week;
        this.userId = userId;
        this.date = date;
        this.orderInDay = orderInDay;
        this.discipline = discipline;
        this.title = title;
        this.detail = detail;
        this.zone = zone;
        this.durationMin = durationMin;
        this.elevationM = elevationM;
        this.rpeMin = rpeMin;
        this.rpeMax = rpeMax;
        this.status = SessionStatus.PLANNED;
    }

    public UUID getId() {
        return id;
    }

    public PlanWeek getWeek() {
        return week;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getOrderInDay() {
        return orderInDay;
    }

    public Discipline getDiscipline() {
        return discipline;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getZone() {
        return zone;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public Integer getElevationM() {
        return elevationM;
    }

    public Integer getRpeMin() {
        return rpeMin;
    }

    public Integer getRpeMax() {
        return rpeMax;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public String getComment() {
        return comment;
    }

    public void updateComment(String comment) {
        this.comment = comment;
    }

    public String getWorkoutJson() {
        return workoutJson;
    }

    public void updateWorkoutJson(String workoutJson) {
        this.workoutJson = workoutJson;
    }

    public void updateStatus(SessionStatus status) {
        this.status = status;
    }

    /** Revise what the session prescribes — the caller passes the final merged values. */
    public void updateContent(String title, String detail, String zone, Integer durationMin,
                              Integer elevationM, Integer rpeMin, Integer rpeMax) {
        this.title = title;
        this.detail = detail;
        this.zone = zone;
        this.durationMin = durationMin;
        this.elevationM = elevationM;
        this.rpeMin = rpeMin;
        this.rpeMax = rpeMax;
    }

    public com.cavale.gym.domain.GymTemplateVariant getTemplateVariant() {
        return templateVariant;
    }

    public void linkTemplateVariant(com.cavale.gym.domain.GymTemplateVariant variant) {
        this.templateVariant = variant;
    }

    /** Reschedule within the plan; a still-planned session becomes MOVED. */
    public void moveTo(LocalDate newDate, int newOrderInDay) {
        boolean dateChanged = !this.date.equals(newDate);
        this.date = newDate;
        this.orderInDay = newOrderInDay;
        if (dateChanged && this.status == SessionStatus.PLANNED) {
            this.status = SessionStatus.MOVED;
        }
    }

    /**
     * Move the session to another week of the same plan — kept in sync with the
     * date so weekly progress counts its load in the week it actually lands in.
     */
    public void reassignWeek(PlanWeek newWeek) {
        this.week = newWeek;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PlannedSession other = (PlannedSession) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
