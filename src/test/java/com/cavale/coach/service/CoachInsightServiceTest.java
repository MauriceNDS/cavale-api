package com.cavale.coach.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.coach.domain.CoachProposal;
import com.cavale.coach.domain.ProposalKind;
import com.cavale.coach.domain.ProposalStatus;
import com.cavale.coach.domain.WeeklyInsight;
import com.cavale.coach.repository.CoachProposalRepository;
import com.cavale.coach.repository.WeeklyInsightRepository;
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.service.TrainingPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachInsightServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 20);
    private static final UUID SESSION = UUID.randomUUID();

    @Mock
    private WeeklyInsightRepository insightRepository;

    @Mock
    private CoachProposalRepository proposalRepository;

    @Mock
    private TrainingPlanService planService;

    private CoachInsightService service() {
        return new CoachInsightService(insightRepository, proposalRepository, planService);
    }

    @Test
    void submit_createsInsightWithParsedProposals() {
        when(insightRepository.findByUserIdAndWeekStart(USER, MONDAY)).thenReturn(Optional.empty());
        when(insightRepository.save(any(WeeklyInsight.class))).thenAnswer(inv -> inv.getArgument(0));

        WeeklyInsight insight = service().submit(USER, MONDAY, "Bonne semaine.",
                "[{\"kind\":\"MOVE_SESSION\",\"sessionId\":\"" + SESSION + "\","
                        + "\"payload\":{\"date\":\"2026-07-31\"},\"rationale\":\"Récup après le trek\"}]");

        assertThat(insight.getProse()).isEqualTo("Bonne semaine.");
        assertThat(insight.getProposals()).hasSize(1);
        CoachProposal proposal = insight.getProposals().getFirst();
        assertThat(proposal.getKind()).isEqualTo(ProposalKind.MOVE_SESSION);
        assertThat(proposal.getSessionId()).isEqualTo(SESSION);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }

    @Test
    void submit_replacesExistingWeekAndItsProposals() {
        WeeklyInsight existing = new WeeklyInsight(USER, MONDAY, "Ancien texte");
        existing.addProposal(new CoachProposal(existing, ProposalKind.SKIP_SESSION, SESSION, "{}", null));
        when(insightRepository.findByUserIdAndWeekStart(USER, MONDAY)).thenReturn(Optional.of(existing));

        WeeklyInsight insight = service().submit(USER, MONDAY, "Nouveau texte", null);

        assertThat(insight).isSameAs(existing);
        assertThat(insight.getProse()).isEqualTo("Nouveau texte");
        assertThat(insight.getProposals()).isEmpty();
    }

    @Test
    void submit_rejectsNonMondayAndBadProposals() {
        assertThatThrownBy(() -> service().submit(USER, MONDAY.plusDays(1), "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monday");

        when(insightRepository.findByUserIdAndWeekStart(USER, MONDAY)).thenReturn(Optional.empty());
        when(insightRepository.save(any(WeeklyInsight.class))).thenAnswer(inv -> inv.getArgument(0));
        // MOVE_SESSION without a date must fail at submit time, not at apply time
        assertThatThrownBy(() -> service().submit(USER, MONDAY, "x",
                "[{\"kind\":\"MOVE_SESSION\",\"sessionId\":\"" + SESSION + "\",\"payload\":{}}]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
        assertThatThrownBy(() -> service().submit(USER, MONDAY, "x", "{\"not\":\"an array\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CoachProposal pendingProposal(ProposalKind kind, String payload) {
        WeeklyInsight insight = new WeeklyInsight(USER, MONDAY, "Prose");
        CoachProposal proposal = new CoachProposal(insight, kind, SESSION, payload, null);
        ReflectionTestUtils.setField(proposal, "id", UUID.randomUUID());
        when(proposalRepository.findById(proposal.getId())).thenReturn(Optional.of(proposal));
        return proposal;
    }

    @Test
    void applyProposal_moveRoutesThroughUpdateSession() {
        CoachProposal proposal = pendingProposal(ProposalKind.MOVE_SESSION, "{\"date\":\"2026-07-31\"}");

        service().applyProposal(USER, proposal.getId());

        ArgumentCaptor<UpdateSessionRequest> captor = ArgumentCaptor.forClass(UpdateSessionRequest.class);
        verify(planService).updateSession(eq(USER), eq(SESSION), captor.capture());
        assertThat(captor.getValue().date()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPLIED);
        assertThat(proposal.getResolvedAt()).isNotNull();
    }

    @Test
    void applyProposal_skipSetsSkippedStatus() {
        CoachProposal proposal = pendingProposal(ProposalKind.SKIP_SESSION, "{}");

        service().applyProposal(USER, proposal.getId());

        ArgumentCaptor<UpdateSessionRequest> captor = ArgumentCaptor.forClass(UpdateSessionRequest.class);
        verify(planService).updateSession(eq(USER), eq(SESSION), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SessionStatus.SKIPPED);
    }

    @Test
    void dismissProposal_resolvesWithoutTouchingThePlan() {
        CoachProposal proposal = pendingProposal(ProposalKind.UPDATE_SESSION, "{\"durationMin\":45}");

        service().dismissProposal(USER, proposal.getId());

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.DISMISSED);
        org.mockito.Mockito.verifyNoInteractions(planService);
    }

    @Test
    void resolvedProposal_cannotBeResolvedAgain_andForeignIs404() {
        CoachProposal proposal = pendingProposal(ProposalKind.SKIP_SESSION, "{}");
        service().dismissProposal(USER, proposal.getId());

        assertThatThrownBy(() -> service().applyProposal(USER, proposal.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already");

        CoachProposal other = pendingProposal(ProposalKind.SKIP_SESSION, "{}");
        assertThatThrownBy(() -> service().applyProposal(STRANGER, other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
