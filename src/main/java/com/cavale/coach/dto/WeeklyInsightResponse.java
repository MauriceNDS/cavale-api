package com.cavale.coach.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cavale.coach.domain.CoachProposal;
import com.cavale.coach.domain.ProposalKind;
import com.cavale.coach.domain.ProposalStatus;
import com.cavale.coach.domain.WeeklyInsight;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record WeeklyInsightResponse(
        UUID id,
        LocalDate weekStart,
        String prose,
        Instant createdAt,
        List<ProposalResponse> proposals) {

    public record ProposalResponse(
            UUID id,
            ProposalKind kind,
            UUID sessionId,
            JsonNode payload,
            String rationale,
            ProposalStatus status,
            Instant resolvedAt) {

        static ProposalResponse from(CoachProposal proposal, ObjectMapper mapper) {
            JsonNode payload;
            try {
                payload = mapper.readTree(proposal.getPayload());
            } catch (JsonProcessingException e) {
                payload = mapper.createObjectNode();
            }
            return new ProposalResponse(proposal.getId(), proposal.getKind(),
                    proposal.getSessionId(), payload, proposal.getRationale(),
                    proposal.getStatus(), proposal.getResolvedAt());
        }
    }

    public static WeeklyInsightResponse from(WeeklyInsight insight, ObjectMapper mapper) {
        return new WeeklyInsightResponse(insight.getId(), insight.getWeekStart(),
                insight.getProse(), insight.getCreatedAt(),
                insight.getProposals().stream().map(p -> ProposalResponse.from(p, mapper)).toList());
    }
}
