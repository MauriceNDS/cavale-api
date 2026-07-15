package com.cavale.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Missed-work analysis for the forward-rebuild (never shuffle-or-stack). The
 * tier says how big the intervention is; redistribution proposes how much
 * volume to add back to each upcoming week — never all of it, hard days first,
 * never two hard days back-to-back. The coach applies it and explains it.
 */
public record PlanRealignResponse(
        Tier tier,
        int missedSessions,
        int missedRunSessions,
        BigDecimal missedVolumeKm,
        List<WeekAdjustment> redistribution,
        String guidance) {

    public enum Tier {
        /** ≤1 miss — let it go. */
        IGNORE,
        /** A day or two off — reschedule within the week. */
        RESCHEDULE,
        /** 3+ sessions or a full week — rebuild the upcoming weeks forward. */
        REBUILD,
        /** 2+ weeks — rebuild to the race date or push it out. */
        EXTEND,
        /** 4+ weeks — the block is lost; restart from a fresh base. */
        RESTART
    }

    public record WeekAdjustment(LocalDate weekStart, BigDecimal addKm) {
    }
}
