package com.cavale.training.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.athlete.service.RunningStatsService;
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.PlanRealignResponse;
import com.cavale.training.dto.PlanRealignResponse.Tier;
import com.cavale.training.dto.PlanRealignResponse.WeekAdjustment;
import com.cavale.training.dto.PlanValidationResponse;
import com.cavale.training.pace.PaceModelService;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;
import com.cavale.training.workout.WorkoutParser;
import com.cavale.training.workout.WorkoutStructure.Allure;

/**
 * The deterministic half of the intelligent coach (P13–P15): it builds a
 * scientifically periodized <em>skeleton</em> (base→build→peak→taper, deload
 * every ~4th week, progressive overload, road/trail + balance/performance
 * aware), validates a generated plan's structure, and analyses missed work for
 * the forward-rebuild. The MCP client (the athlete's Claude) fills the creative
 * content — sessions, wording — on top, guided by the McpConfig instructions.
 */
@Service
public class PlanCoachService {

    /** Weeks between deloads in a classic 3:1 loading cycle. */
    private static final int DELOAD_EVERY = 4;
    private static final double MIN_BASE_KM = 20;

    private final ObjectiveRepository objectiveRepository;
    private final PlanWeekRepository weekRepository;
    private final PlannedSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;
    private final TrainingPlanService planService;
    private final RunningStatsService runningStatsService;
    private final SessionTemplateService templateService;
    private final PaceModelService paceModelService;

    public PlanCoachService(ObjectiveRepository objectiveRepository,
                            PlanWeekRepository weekRepository,
                            PlannedSessionRepository sessionRepository,
                            ActivityRepository activityRepository,
                            TrainingPlanService planService,
                            RunningStatsService runningStatsService,
                            SessionTemplateService templateService,
                            PaceModelService paceModelService) {
        this.objectiveRepository = objectiveRepository;
        this.weekRepository = weekRepository;
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
        this.planService = planService;
        this.runningStatsService = runningStatsService;
        this.templateService = templateService;
        this.paceModelService = paceModelService;
    }

    /* ── P13: scaffold ─────────────────────────────────────────────────── */

    private record WeekSpec(WeekType type, String phase, double km, int elevationM, String focus) {
    }

    /**
     * Build the periodized week skeleton for an (empty) plan: week types,
     * phases and progressive target loads from the athlete's current volume up
     * to a peak, then a taper. The caller fills the sessions.
     */
    @Transactional
    public List<PlanWeek> scaffold(UUID userId, UUID planId, LocalDate today) {
        return scaffold(userId, planId, today, false);
    }

    /**
     * Scaffold the week skeleton, optionally filling every week with the
     * deterministic default sessions ({@link SessionTemplateService}) — a plan
     * usable as-is with zero AI, or a baseline the AI coach then edits.
     */
    @Transactional
    public List<PlanWeek> scaffold(UUID userId, UUID planId, LocalDate today, boolean fillSessions) {
        TrainingPlan plan = planService.getOwnedPlan(userId, planId);
        if (!weekRepository.findByPlanIdOrderByWeekNumber(planId).isEmpty()) {
            throw new IllegalArgumentException(
                    "This plan already has weeks — scaffold only builds an empty plan");
        }
        Objective main = objectiveRepository.findByPlanIdAndRole(planId, ObjectiveRole.MAIN)
                .orElseThrow(() -> new ResourceNotFoundException("Main objective", planId));

        LocalDate firstMonday = plan.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (firstMonday.isBefore(plan.getStartDate())) {
            // weeks are Monday-anchored and addWeek rejects a start outside the
            // plan — a mid-week season begins with a partial, week-less stub
            firstMonday = firstMonday.plusWeeks(1);
        }
        LocalDate raceDate = main.getDate() != null ? main.getDate() : plan.getEndDate();
        LocalDate raceMonday = raceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weeks = (int) Math.min(40, Math.max(1,
                ChronoUnit.WEEKS.between(firstMonday, raceMonday) + 1));

        List<WeekSpec> specs = periodize(weeks, main, baseWeeklyKm(userId, today, main));

        List<PlanWeek> created = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            WeekSpec spec = specs.get(i);
            LocalDate weekStart = firstMonday.plusWeeks(i);
            created.add(planService.addWeek(userId, planId, new CreateWeekRequest(i + 1, weekStart,
                    spec.phase(), spec.type(), BigDecimal.valueOf(spec.km()).setScale(0, RoundingMode.HALF_UP),
                    spec.elevationM() > 0 ? spec.elevationM() : null, null, spec.focus())));
        }
        if (fillSessions) {
            var pace = paceModelService.modelFor(userId);
            for (PlanWeek week : created) {
                templateService.fillWeek(userId, plan, week, main, pace);
            }
        }
        return created;
    }

    /**
     * The objective's TYPE decides the season's whole shape: a race gets the
     * classic base→build→peak→taper arc, the non-race types get honest
     * alternatives instead of a fake race countdown.
     */
    private List<WeekSpec> periodize(int weeks, Objective main, double baseKm) {
        return switch (main.getType()) {
            case RACE -> racePeriodization(weeks, main, baseKm);
            case FITNESS -> fitnessPeriodization(weeks, main, baseKm);
            case RECOVERY -> recoveryPeriodization(weeks, main, baseKm);
            case GENERAL -> maintenancePeriodization(weeks, main, baseKm);
        };
    }

    private List<WeekSpec> racePeriodization(int weeks, Objective main, double baseKm) {
        boolean trail = main.getKind() == ObjectiveKind.TRAIL;
        boolean performance = main.getIntensity() == ObjectiveIntensity.PERFORMANCE;
        double dPlusPerKm = trail ? raceDPlusPerKm(main) : 0;
        double peakKm = baseKm * (performance ? 1.8 : 1.5);

        int raceIdx = weeks - 1;
        int taperWeeks = weeks >= 8 ? 2 : weeks >= 4 ? 1 : 0;
        int lastBuildIdx = raceIdx - taperWeeks - 1;
        int trainingWeeks = raceIdx - taperWeeks;
        int baseCount = Math.max(1, Math.round(trainingWeeks * 0.30f));

        List<WeekSpec> specs = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            if (i == raceIdx) {
                specs.add(new WeekSpec(WeekType.RACE, "Course",
                        main.getDistanceKm() != null ? main.getDistanceKm().doubleValue() : baseKm * 0.4,
                        trail && main.getElevationGainM() != null ? main.getElevationGainM() : 0,
                        "Semaine de course — objectif"));
                continue;
            }
            if (i > lastBuildIdx) {
                int toRace = raceIdx - i; // 1 = the week right before the race
                double factor = toRace == 1 ? 0.45 : 0.6; // exponential-ish cut (55 %, then 40 %)
                double km = peakKm * factor;
                specs.add(new WeekSpec(WeekType.TAPER, "Affûtage", km, trailElevation(km, dPlusPerKm),
                        "Affûtage — volume réduit, intensité et fréquence maintenues"));
                continue;
            }
            double ramp = lastBuildIdx > 0 ? (double) i / lastBuildIdx : 1;
            double km = baseKm + (peakKm - baseKm) * ramp;
            boolean deload = (i + 1) % DELOAD_EVERY == 0 && i != lastBuildIdx;
            WeekType type;
            String phase;
            if (deload) {
                km *= 0.6;
                type = WeekType.DELOAD;
                phase = "Assimilation";
            } else if (i < baseCount) {
                type = WeekType.BUILD;
                phase = "Base";
            } else if (i >= lastBuildIdx - 1) {
                type = WeekType.SHOCK;
                phase = "Spécifique";
            } else {
                type = WeekType.BUILD;
                phase = "Développement";
            }
            specs.add(new WeekSpec(type, phase, km, trailElevation(km, dPlusPerKm), phaseFocus(phase)));
        }
        return specs;
    }

    /**
     * FITNESS: rolling build/deload cycles ramping toward a sustainable peak —
     * no taper, no race week, the last week absorbs like any other.
     */
    private List<WeekSpec> fitnessPeriodization(int weeks, Objective main, double baseKm) {
        boolean trail = main.getKind() == ObjectiveKind.TRAIL;
        boolean performance = main.getIntensity() == ObjectiveIntensity.PERFORMANCE;
        double dPlusPerKm = trail ? raceDPlusPerKm(main) : 0;
        double peakKm = baseKm * (performance ? 1.5 : 1.3);
        int baseCount = Math.max(1, Math.round(weeks * 0.25f));

        List<WeekSpec> specs = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            double ramp = weeks > 1 ? (double) i / (weeks - 1) : 1;
            double km = baseKm + (peakKm - baseKm) * ramp;
            if ((i + 1) % DELOAD_EVERY == 0) {
                km *= 0.6;
                specs.add(new WeekSpec(WeekType.DELOAD, "Assimilation", km,
                        trailElevation(km, dPlusPerKm), phaseFocus("Assimilation")));
            } else {
                String phase = i < baseCount ? "Base" : "Développement";
                specs.add(new WeekSpec(WeekType.BUILD, phase, km,
                        trailElevation(km, dPlusPerKm), phaseFocus(phase)));
            }
        }
        return specs;
    }

    /** RECOVERY: capped, gently rising easy volume — no intensity anywhere. */
    private List<WeekSpec> recoveryPeriodization(int weeks, Objective main, double baseKm) {
        boolean trail = main.getKind() == ObjectiveKind.TRAIL;
        double dPlusPerKm = trail ? raceDPlusPerKm(main) : 0;

        List<WeekSpec> specs = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            double ramp = weeks > 1 ? (double) i / (weeks - 1) : 1;
            double km = baseKm * (0.4 + 0.35 * ramp);
            specs.add(new WeekSpec(WeekType.RECOVERY, "Reprise", km,
                    trailElevation(km, dPlusPerKm), phaseFocus("Reprise")));
        }
        return specs;
    }

    /** GENERAL: steady maintenance at the current volume, deload every 4th week. */
    private List<WeekSpec> maintenancePeriodization(int weeks, Objective main, double baseKm) {
        boolean trail = main.getKind() == ObjectiveKind.TRAIL;
        double dPlusPerKm = trail ? raceDPlusPerKm(main) : 0;

        List<WeekSpec> specs = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            boolean deload = (i + 1) % DELOAD_EVERY == 0;
            double km = baseKm * (deload ? 0.7 : 1.0);
            specs.add(new WeekSpec(deload ? WeekType.DELOAD : WeekType.BUILD,
                    deload ? "Assimilation" : "Entretien", km,
                    trailElevation(km, dPlusPerKm),
                    phaseFocus(deload ? "Assimilation" : "Entretien")));
        }
        return specs;
    }

    private static double raceDPlusPerKm(Objective main) {
        if (main.getElevationGainM() != null && main.getDistanceKm() != null
                && main.getDistanceKm().doubleValue() > 0) {
            return main.getElevationGainM() / main.getDistanceKm().doubleValue();
        }
        return 25; // a plausible trail default when the race profile is unknown
    }

    private static int trailElevation(double km, double dPlusPerKm) {
        return (int) Math.round(km * dPlusPerKm);
    }

    private static String phaseFocus(String phase) {
        return switch (phase) {
            case "Base" -> "Base aérobie — volume facile, une touche de qualité";
            case "Spécifique" -> "Spécifique course — allure objectif et dénivelé spécifique";
            case "Assimilation" -> "Semaine allégée — on assimile la charge";
            case "Reprise" -> "Reprise — volume réduit, aucune intensité, on écoute le corps";
            case "Entretien" -> "Entretien — régularité et plaisir, volume stable";
            default -> "Développement — volume et seuil en progression";
        };
    }

    /** Average of the last weeks the athlete actually ran, floored to a sane base. */
    private double baseWeeklyKm(UUID userId, LocalDate today, Objective main) {
        var stats = runningStatsService.getStats(userId, today);
        List<Double> recent = stats.weeklyVolume().stream()
                .filter(w -> w.runs() > 0)
                .map(w -> w.distanceKm().doubleValue())
                .toList();
        double avg = recent.isEmpty() ? 0
                : recent.subList(Math.max(0, recent.size() - 6), recent.size()).stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);
        double raceFloor = main.getDistanceKm() != null ? main.getDistanceKm().doubleValue() * 0.35 : 0;
        return Math.max(Math.max(avg, raceFloor), MIN_BASE_KM);
    }

    /* ── P13: validate ─────────────────────────────────────────────────── */

    @Transactional(readOnly = true)
    public PlanValidationResponse validate(UUID userId, UUID planId) {
        planService.getOwnedPlan(userId, planId);
        List<PlanWeek> weeks = weekRepository.findByPlanIdOrderByWeekNumber(planId);
        List<PlannedSession> sessions = sessionRepository.findByWeekPlanId(planId);
        List<String> issues = new ArrayList<>();

        if (weeks.isEmpty()) {
            return PlanValidationResponse.of(List.of("The plan has no weeks — scaffold it first."));
        }

        Map<UUID, List<PlannedSession>> byWeek = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getWeek().getId()));
        for (PlanWeek week : weeks) {
            if (week.getWeekType() == WeekType.TAPER || week.getWeekType() == WeekType.RACE) {
                continue;
            }
            long runs = byWeek.getOrDefault(week.getId(), List.of()).stream()
                    .filter(s -> s.getDiscipline() == Discipline.RUN)
                    .count();
            if (runs == 0) {
                issues.add("Week " + week.getWeekNumber() + " (" + week.getWeekType()
                        + ") has no running session.");
            }
        }

        for (PlannedSession session : sessions) {
            if (session.getDiscipline() != Discipline.RUN) {
                continue;
            }
            if (WorkoutParser.parse(session.getDetail(), session.getZone(),
                    session.getDurationMin()).nodes().isEmpty()) {
                issues.add("Session '" + session.getTitle() + "' on " + session.getDate()
                        + " has no usable workout — give it a detail or a duration.");
            }
            // Strength work tacked onto a run's text is invisible in the plan:
            // it can't be started, tracked or counted. It belongs in its own
            // GYM session on the same day. Only worth flagging while the
            // session is still owed — a DONE or SKIPPED one records what WAS
            // prescribed, and rewriting history is not a fix.
            if (session.getStatus().isPending() && session.getDetail() != null
                    && WorkoutParser.STRENGTH_WORK.matcher(session.getDetail()).find()) {
                issues.add("Run '" + session.getTitle() + "' on " + session.getDate()
                        + " prescribes strength/core work in its detail — give it its own GYM "
                        + "session that day (with a templateVariantId) and drop it from the run text.");
            }
        }

        List<LocalDate> hardDays = sessions.stream()
                .filter(PlanCoachService::isHard)
                .map(PlannedSession::getDate)
                .distinct()
                .sorted()
                .toList();
        for (int i = 1; i < hardDays.size(); i++) {
            if (ChronoUnit.DAYS.between(hardDays.get(i - 1), hardDays.get(i)) == 1) {
                issues.add("Two hard days back-to-back on " + hardDays.get(i - 1)
                        + " and " + hardDays.get(i) + " — separate hard sessions with an easy day.");
            }
        }

        if (weeks.size() >= 6) {
            boolean raceSeason = objectiveRepository.findByPlanIdAndRole(planId, ObjectiveRole.MAIN)
                    .map(o -> o.getType() == ObjectiveType.RACE)
                    .orElse(true);
            if (raceSeason && weeks.stream().noneMatch(w -> w.getWeekType() == WeekType.TAPER)) {
                issues.add("No taper before the race — end with ~2 reduced-volume weeks.");
            }
            if (weeks.stream().noneMatch(w -> w.getWeekType() == WeekType.DELOAD
                    || w.getWeekType() == WeekType.RECOVERY)) {
                issues.add("No deload week — insert a lighter week roughly every 4th week.");
            }
        }
        return PlanValidationResponse.of(issues);
    }

    /* ── P14: realign (forward-rebuild) ───────────────────────────────── */

    @Transactional(readOnly = true)
    public PlanRealignResponse realign(UUID userId, UUID planId, LocalDate today) {
        planService.getOwnedPlan(userId, planId);
        List<PlannedSession> sessions = sessionRepository.findByWeekPlanId(planId);
        LocalDate recentFrom = today.minusWeeks(3);

        List<PlannedSession> missed = sessions.stream()
                .filter(s -> s.getDiscipline() != Discipline.REST)
                .filter(s -> (s.getStatus().isPending() && s.getDate().isBefore(today))
                        || (s.getStatus() == SessionStatus.SKIPPED && !s.getDate().isBefore(recentFrom)))
                .toList();

        List<PlannedSession> missedRuns = missed.stream()
                .filter(s -> s.getDiscipline() == Discipline.RUN)
                .toList();
        double missedKm = missedRuns.stream()
                .filter(s -> s.getDurationMin() != null)
                .mapToDouble(s -> s.getDurationMin() / 6.0) // ~10 km/h estimate from duration
                .sum();
        long missedWeeks = missed.stream()
                .map(s -> s.getDate().with(DayOfWeek.MONDAY))
                .distinct()
                .count();

        Tier tier = tier(missed.size(), missedWeeks);
        List<WeekAdjustment> redistribution = redistribution(planId, today, tier, missedKm);

        // Unplanned hikes are EXTRA leg load the plan never asked for — the
        // inverse of a missed session, and just as relevant to the rebuild.
        List<Activity> hikes = activityRepository
                .findByUserIdAndSessionIsNullAndDateBetween(userId, recentFrom, today)
                .stream()
                .filter(a -> a.getDiscipline() == Discipline.HIKE)
                .toList();
        double hikeKmEffort = hikes.stream()
                .mapToDouble(a -> (a.getDistanceKm() != null ? a.getDistanceKm().doubleValue() : 0)
                        + (a.getElevationM() != null ? a.getElevationM() / 100.0 : 0))
                .sum();

        String guidance = guidance(tier);
        if (!hikes.isEmpty()) {
            guidance += " Also: " + hikes.size() + " unplanned hike(s) added ~"
                    + Math.round(hikeKmEffort) + " km-effort of leg load — count it as easy "
                    + "volume already done and trim the coming week's easy running accordingly.";
        }

        return new PlanRealignResponse(tier, missed.size(), missedRuns.size(),
                BigDecimal.valueOf(missedKm).setScale(0, RoundingMode.HALF_UP), redistribution,
                hikes.size(), BigDecimal.valueOf(hikeKmEffort).setScale(0, RoundingMode.HALF_UP),
                guidance);
    }

    private static Tier tier(int missedCount, long missedWeeks) {
        if (missedCount <= 1) {
            return Tier.IGNORE;
        }
        if (missedWeeks <= 1 && missedCount <= 2) {
            return Tier.RESCHEDULE;
        }
        if (missedWeeks >= 4) {
            return Tier.RESTART;
        }
        if (missedWeeks >= 2) {
            return Tier.EXTEND;
        }
        return Tier.REBUILD;
    }

    /** Add back 60 % of the missed volume across the next up-to-4 non-taper weeks. */
    private List<WeekAdjustment> redistribution(UUID planId, LocalDate today, Tier tier, double missedKm) {
        if (tier == Tier.IGNORE || tier == Tier.RESTART || missedKm <= 0) {
            return List.of();
        }
        LocalDate fromMonday = today.with(DayOfWeek.MONDAY);
        List<PlanWeek> upcoming = weekRepository.findByPlanIdOrderByWeekNumber(planId).stream()
                .filter(w -> !w.getStartDate().isBefore(fromMonday))
                .filter(w -> w.getWeekType() != WeekType.TAPER && w.getWeekType() != WeekType.RACE)
                .limit(4)
                .toList();
        if (upcoming.isEmpty()) {
            return List.of();
        }
        double perWeek = missedKm * 0.6 / upcoming.size();
        return upcoming.stream()
                .map(w -> new WeekAdjustment(w.getStartDate(),
                        BigDecimal.valueOf(perWeek).setScale(0, RoundingMode.HALF_UP)))
                .toList();
    }

    private static String guidance(Tier tier) {
        return switch (tier) {
            case IGNORE -> "One missed session at most — carry on, no change needed.";
            case RESCHEDULE -> "A day or two lost — reschedule the key session inside the current week; "
                    + "don't stack it onto an already-hard day.";
            case REBUILD -> "Rebuild forward against the fixed race date: add ~60 % of the missed volume "
                    + "across the next weeks, hard days first, never two hard days back-to-back — drop the rest.";
            case EXTEND -> "Two weeks or more lost: rebuild toward the race date, or push the race out if it "
                    + "can move. Re-establish the base before resuming intensity.";
            case RESTART -> "A month or more is gone — the block is lost. Restart from a fresh, conservative "
                    + "base rather than cramming; re-scaffold if the race is still far enough away.";
        };
    }

    /* ── Shared: what counts as a hard session ─────────────────────────── */

    public static boolean isHard(PlannedSession session) {
        return isHard(session.getDiscipline(), session.getZone(), session.getRpeMax());
    }

    /** A RUN carrying real intensity — threshold/VO2max/sprint work, or RPE ≥ 7. */
    public static boolean isHard(Discipline discipline, String zone, Integer rpeMax) {
        if (discipline != Discipline.RUN) {
            return false;
        }
        if (rpeMax != null && rpeMax >= 7) {
            return true;
        }
        Allure allure = WorkoutParser.allureOfZone(zone);
        return allure == Allure.SEUIL60 || allure == Allure.SEUIL30
                || allure == Allure.VMA || allure == Allure.SPRINT;
    }
}
