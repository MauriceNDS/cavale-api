package com.cavale.gym.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;
import com.cavale.training.domain.PerceivedEffort;
import com.cavale.training.domain.PlannedSession;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * One strength session actually performed. Attached to a planned session it
 * is that session's validation; standalone it is an ad-hoc workout. The
 * template name is a snapshot — history stays readable after renames.
 */
@Entity
@Table(name = "workout_log")
public class WorkoutLog extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_session_id", unique = true)
    private PlannedSession session;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "template_variant_id")
    private GymTemplateVariant templateVariant;

    @Column(name = "template_name", length = 170)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private WorkoutStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "perceived_effort", length = 15)
    private PerceivedEffort perceivedEffort;

    @Column(name = "pain_flag", nullable = false)
    private boolean painFlag;

    @Column(columnDefinition = "text")
    private String comment;

    protected WorkoutLog() {
    }

    public WorkoutLog(UUID userId, PlannedSession session, GymTemplateVariant templateVariant,
                      String templateName, Instant startedAt) {
        this.userId = userId;
        this.session = session;
        this.templateVariant = templateVariant;
        this.templateName = templateName;
        this.status = WorkoutStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public void finish(Integer durationMin, PerceivedEffort perceivedEffort, boolean painFlag,
                       String comment) {
        this.status = WorkoutStatus.FINISHED;
        this.durationMin = durationMin;
        this.perceivedEffort = perceivedEffort;
        this.painFlag = painFlag;
        this.comment = comment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public PlannedSession getSession() {
        return session;
    }

    public GymTemplateVariant getTemplateVariant() {
        return templateVariant;
    }

    public String getTemplateName() {
        return templateName;
    }

    public WorkoutStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public PerceivedEffort getPerceivedEffort() {
        return perceivedEffort;
    }

    public boolean isPainFlag() {
        return painFlag;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        WorkoutLog other = (WorkoutLog) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
