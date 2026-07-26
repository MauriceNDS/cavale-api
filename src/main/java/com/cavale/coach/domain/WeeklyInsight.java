package com.cavale.coach.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.cavale.common.domain.Auditable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * The coach's weekly review: what happened last week versus the plan, in
 * prose, plus structured {@link CoachProposal}s for the week ahead. One per
 * athlete per ISO week — resubmitting replaces the previous review (the
 * generator may be re-run). Written by the external weekly agent over MCP;
 * read (and its proposals resolved) from the app.
 */
@Entity
@Table(name = "weekly_insight")
public class WeeklyInsight extends Auditable {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Monday of the ISO week the review covers. */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, columnDefinition = "text")
    private String prose;

    @OneToMany(mappedBy = "insight", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc, id asc")
    private List<CoachProposal> proposals = new ArrayList<>();

    protected WeeklyInsight() {
    }

    public WeeklyInsight(UUID userId, LocalDate weekStart, String prose) {
        this.userId = userId;
        this.weekStart = weekStart;
        this.prose = prose;
    }

    /** Resubmission for the same week: new prose, previous proposals dropped. */
    public void replaceContent(String prose) {
        this.prose = prose;
        this.proposals.clear();
    }

    public void addProposal(CoachProposal proposal) {
        proposals.add(proposal);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public String getProse() {
        return prose;
    }

    public List<CoachProposal> getProposals() {
        return proposals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        WeeklyInsight other = (WeeklyInsight) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
