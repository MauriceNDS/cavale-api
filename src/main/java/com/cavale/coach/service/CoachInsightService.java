package com.cavale.coach.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.coach.domain.CoachProposal;
import com.cavale.coach.domain.ProposalKind;
import com.cavale.coach.domain.ProposalStatus;
import com.cavale.coach.domain.WeeklyInsight;
import com.cavale.coach.repository.CoachProposalRepository;
import com.cavale.coach.repository.WeeklyInsightRepository;
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.service.TrainingPlanService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The weekly coach's storage and its ONLY write path into the plan. The
 * external agent submits one insight per ISO week (prose + proposals) over
 * MCP; the athlete applies or dismisses each proposal in the app. Applying
 * routes through {@link TrainingPlanService}, so every guardrail (move guard,
 * ownership, plan range) holds for the coach exactly as for a human.
 *
 * Proposal payload shapes (JSON):
 * - MOVE_SESSION   {"date": "2026-08-07", "orderInDay": 0?}
 * - UPDATE_SESSION {"title"?, "detail"?, "zone"?, "durationMin"?,
 *                   "elevationM"?, "rpeMin"?, "rpeMax"?, "comment"?}
 * - SKIP_SESSION   {}
 * - ADD_SESSION    {"weekId": "…", "date": "…", "discipline": "RUN",
 *                   "title": "…", "detail"?, "zone"?, "durationMin"?,
 *                   "elevationM"?, "rpeMin"?, "rpeMax"?}
 */
@Service
public class CoachInsightService {

    private static final int MAX_PROPOSALS = 10;

    private final WeeklyInsightRepository insightRepository;
    private final CoachProposalRepository proposalRepository;
    private final TrainingPlanService planService;
    // payloads are self-contained JSON — a plain mapper, no Spring config needed
    private final ObjectMapper mapper = new ObjectMapper();

    public CoachInsightService(WeeklyInsightRepository insightRepository,
                               CoachProposalRepository proposalRepository,
                               TrainingPlanService planService) {
        this.insightRepository = insightRepository;
        this.proposalRepository = proposalRepository;
        this.planService = planService;
    }

    /** Upsert the week's review — resubmitting replaces prose AND proposals. */
    @Transactional
    public WeeklyInsight submit(UUID userId, LocalDate weekStart, String prose, String proposalsJson) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart must be the Monday of the reviewed week");
        }
        if (prose == null || prose.isBlank()) {
            throw new IllegalArgumentException("prose must not be blank");
        }
        WeeklyInsight insight = insightRepository.findByUserIdAndWeekStart(userId, weekStart)
                .map(existing -> {
                    existing.replaceContent(prose.trim());
                    return existing;
                })
                .orElseGet(() -> insightRepository.save(new WeeklyInsight(userId, weekStart, prose.trim())));
        for (JsonNode node : parseProposals(proposalsJson)) {
            insight.addProposal(buildProposal(insight, node));
        }
        return insight;
    }

    @Transactional(readOnly = true)
    public List<WeeklyInsight> list(UUID userId, int limit) {
        return insightRepository.findByUserIdOrderByWeekStartDesc(userId, Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public Optional<WeeklyInsight> latest(UUID userId) {
        return insightRepository.findByUserIdOrderByWeekStartDesc(userId, Limit.of(1))
                .stream().findFirst();
    }

    @Transactional
    public WeeklyInsight applyProposal(UUID userId, UUID proposalId) {
        CoachProposal proposal = ownedProposal(userId, proposalId);
        JsonNode payload = readPayload(proposal);
        switch (proposal.getKind()) {
            case MOVE_SESSION -> planService.updateSession(userId, proposal.getSessionId(),
                    new UpdateSessionRequest(LocalDate.parse(payload.get("date").asText()),
                            intOrNull(payload, "orderInDay"), null, null, null, null, null,
                            null, null, null, null, null, null));
            case UPDATE_SESSION -> planService.updateSession(userId, proposal.getSessionId(),
                    new UpdateSessionRequest(null, null,
                            textOrNull(payload, "title"), textOrNull(payload, "detail"),
                            textOrNull(payload, "zone"), intOrNull(payload, "durationMin"),
                            intOrNull(payload, "elevationM"), intOrNull(payload, "rpeMin"),
                            intOrNull(payload, "rpeMax"), null,
                            textOrNull(payload, "comment"), null, null));
            case SKIP_SESSION -> planService.updateSession(userId, proposal.getSessionId(),
                    new UpdateSessionRequest(null, null, null, null, null, null, null,
                            null, null, SessionStatus.SKIPPED, null, null, null));
            case ADD_SESSION -> planService.addSession(userId,
                    UUID.fromString(payload.get("weekId").asText()),
                    new CreateSessionRequest(LocalDate.parse(payload.get("date").asText()), 0,
                            Discipline.valueOf(payload.get("discipline").asText()),
                            payload.get("title").asText(),
                            textOrNull(payload, "detail"), textOrNull(payload, "zone"),
                            intOrNull(payload, "durationMin"), intOrNull(payload, "elevationM"),
                            intOrNull(payload, "rpeMin"), intOrNull(payload, "rpeMax"),
                            null, null));
        }
        proposal.resolve(ProposalStatus.APPLIED);
        return proposal.getInsight();
    }

    @Transactional
    public WeeklyInsight dismissProposal(UUID userId, UUID proposalId) {
        CoachProposal proposal = ownedProposal(userId, proposalId);
        proposal.resolve(ProposalStatus.DISMISSED);
        return proposal.getInsight();
    }

    private CoachProposal ownedProposal(UUID userId, UUID proposalId) {
        return proposalRepository.findById(proposalId)
                .filter(p -> p.getInsight().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));
    }

    private List<JsonNode> parseProposals(String proposalsJson) {
        if (proposalsJson == null || proposalsJson.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = mapper.readTree(proposalsJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("proposals is not valid JSON: " + e.getOriginalMessage());
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("proposals must be a JSON array");
        }
        if (root.size() > MAX_PROPOSALS) {
            throw new IllegalArgumentException(
                    "at most " + MAX_PROPOSALS + " proposals per week — keep only the ones that matter");
        }
        List<JsonNode> nodes = new java.util.ArrayList<>();
        root.forEach(nodes::add);
        return nodes;
    }

    private CoachProposal buildProposal(WeeklyInsight insight, JsonNode node) {
        if (node.get("kind") == null) {
            throw new IllegalArgumentException("every proposal needs a kind");
        }
        ProposalKind kind;
        try {
            kind = ProposalKind.valueOf(node.get("kind").asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown proposal kind: " + node.get("kind").asText());
        }
        UUID sessionId = node.hasNonNull("sessionId") ? UUID.fromString(node.get("sessionId").asText()) : null;
        if (kind != ProposalKind.ADD_SESSION && sessionId == null) {
            throw new IllegalArgumentException(kind + " needs a sessionId");
        }
        JsonNode payload = node.get("payload");
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException(kind + " needs a payload object");
        }
        validatePayload(kind, payload);
        return new CoachProposal(insight, kind, sessionId, payload.toString(),
                node.hasNonNull("rationale") ? node.get("rationale").asText() : null);
    }

    /** Fail at SUBMIT time, not apply time — the agent can fix and resubmit. */
    private static void validatePayload(ProposalKind kind, JsonNode payload) {
        switch (kind) {
            case MOVE_SESSION -> requireFields(payload, kind, "date");
            case ADD_SESSION -> requireFields(payload, kind, "weekId", "date", "discipline", "title");
            case UPDATE_SESSION -> {
                if (payload.isEmpty()) {
                    throw new IllegalArgumentException("UPDATE_SESSION payload must change something");
                }
            }
            case SKIP_SESSION -> {
            }
        }
    }

    private static void requireFields(JsonNode payload, ProposalKind kind, String... fields) {
        for (String field : fields) {
            if (!payload.hasNonNull(field)) {
                throw new IllegalArgumentException(kind + " payload needs '" + field + "'");
            }
        }
    }

    private JsonNode readPayload(CoachProposal proposal) {
        try {
            return mapper.readTree(proposal.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored payload is unreadable", e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }
}
