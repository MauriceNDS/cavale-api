package com.cavale.coach.domain;

import java.time.Instant;
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

/**
 * One concrete, applicable change the weekly coach suggests — "move Thursday's
 * threshold to Friday", "cut Sunday's long run to 1h45". The payload is the
 * kind-specific JSON the apply path feeds to the plan services; nothing
 * touches the plan until the athlete applies it.
 */
@Entity
@Table(name = "coach_proposal")
public class CoachProposal extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insight_id", nullable = false)
    private WeeklyInsight insight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalKind kind;

    /** The planned session the proposal targets — null for ADD_SESSION. */
    @Column(name = "session_id")
    private UUID sessionId;

    /** Kind-specific change as JSON (see CoachInsightService for the shapes). */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(columnDefinition = "text")
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProposalStatus status = ProposalStatus.PENDING;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected CoachProposal() {
    }

    public CoachProposal(WeeklyInsight insight, ProposalKind kind, UUID sessionId,
                         String payload, String rationale) {
        this.insight = insight;
        this.kind = kind;
        this.sessionId = sessionId;
        this.payload = payload;
        this.rationale = rationale;
    }

    public void resolve(ProposalStatus outcome) {
        if (status != ProposalStatus.PENDING) {
            throw new IllegalArgumentException("Proposal is already " + status);
        }
        this.status = outcome;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public WeeklyInsight getInsight() {
        return insight;
    }

    public ProposalKind getKind() {
        return kind;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getPayload() {
        return payload;
    }

    public String getRationale() {
        return rationale;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CoachProposal other = (CoachProposal) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
