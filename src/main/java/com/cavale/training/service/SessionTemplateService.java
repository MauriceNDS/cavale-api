package com.cavale.training.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.PlanFocus;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.pace.PaceModel;
import com.cavale.training.workout.WorkoutStructure.Allure;

/**
 * The deterministic session library behind scaffold's optional fill: turns a
 * periodized week (type, phase, targets) plus the plan's preferences
 * (runs/week, gym/week, focus) into a complete, sound default week — long run
 * Sunday, quality Tuesday when the week carries any, easy runs in between,
 * strength on Monday/Friday. Durations come from the athlete's own
 * {@link PaceModel}, vertical from the week's D+ target. The AI coach (or the
 * athlete) then EDITS these sessions instead of authoring from a blank page.
 *
 * Details are written in the French workout notation {@code WorkoutParser}
 * understands, so the structured workout (and the watch export) derive
 * directly from what this service generates.
 */
@Service
public class SessionTemplateService {

    static final int DEFAULT_RUNS_PER_WEEK = 3;
    static final int DEFAULT_GYM_PER_WEEK = 1;

    /** Long-run share of the weekly time budget, by week character. */
    private static final double SL_SHARE = 0.33;
    private static final double SL_SHARE_EASY = 0.28;
    private static final double SL_SHARE_SPECIFIC_TRAIL = 0.38;

    private static final int MIN_EF_MIN = 25;
    private static final int MAX_EF_MIN = 75;

    /** EF fillers, best days first (SL owns Sunday, quality owns Tuesday). */
    private static final int[] EF_DAYS = {3, 5, 2, 4, 0};
    private static final int[] GYM_DAYS = {0, 4, 2};

    private final TrainingPlanService planService;

    public SessionTemplateService(TrainingPlanService planService) {
        this.planService = planService;
    }

    private record QualitySession(String title, String detail, String zone,
                                  int durationMin, int rpeMin, int rpeMax) {
    }

    /** Generate and persist the default sessions of one scaffolded week. */
    @Transactional
    public List<PlannedSession> fillWeek(UUID userId, TrainingPlan plan, PlanWeek week,
                                         Objective main, PaceModel pace) {
        List<CreateSessionRequest> requests = week.getWeekType() == WeekType.RACE
                ? raceWeek(plan, week, main, pace)
                : trainingWeek(plan, week, main, pace);

        Map<LocalDate, Integer> orderByDay = new HashMap<>();
        List<PlannedSession> created = new ArrayList<>();
        for (CreateSessionRequest request : requests) {
            LocalDate date = request.date();
            if (date.isBefore(plan.getStartDate()) || date.isAfter(plan.getEndDate())) {
                continue;
            }
            int order = orderByDay.merge(date, 1, Integer::sum) - 1;
            created.add(planService.addSession(userId, week.getId(), new CreateSessionRequest(
                    date, order, request.discipline(), request.title(), request.detail(),
                    request.zone(), request.durationMin(), request.elevationM(),
                    request.rpeMin(), request.rpeMax(), null, null)));
        }
        return created;
    }

    private List<CreateSessionRequest> trainingWeek(TrainingPlan plan, PlanWeek week,
                                                    Objective main, PaceModel pace) {
        int runs = plan.getRunsPerWeek() != null ? plan.getRunsPerWeek() : DEFAULT_RUNS_PER_WEEK;
        int gyms = plan.getGymPerWeek() != null ? plan.getGymPerWeek() : DEFAULT_GYM_PER_WEEK;
        PlanFocus focus = plan.getFocus() != null ? plan.getFocus() : PlanFocus.MAINTAIN;
        boolean trail = main.getKind() != ObjectiveKind.ROAD;
        WeekType type = week.getWeekType();
        boolean easyWeek = type == WeekType.DELOAD || type == WeekType.RECOVERY
                || type == WeekType.TRANSITION;

        int effectiveRuns = Math.max(1, easyWeek ? Math.min(runs, 3) : runs);
        QualitySession quality = easyWeek || effectiveRuns < 2
                ? null
                : quality(week, focus, trail);

        double weekKm = week.getTargetVolumeKm() != null
                ? week.getTargetVolumeKm().doubleValue() : 30;
        int weekDPlus = trail && week.getTargetElevationM() != null ? week.getTargetElevationM() : 0;
        // the week's whole running time budget, split across the sessions
        long budgetSec = Math.round(weekKm * pace.secPerKm(Allure.EF)
                + weekDPlus * pace.climbSecPerMeter());

        double slShare = easyWeek ? SL_SHARE_EASY
                : "Spécifique".equals(week.getPhase()) && trail ? SL_SHARE_SPECIFIC_TRAIL
                : SL_SHARE;
        int slDPlus = weekDPlus > 0 ? (int) Math.round(weekDPlus * 0.6) : 0;
        int slMin = effectiveRuns == 1
                ? roundTo5(budgetSec / 60.0)
                : Math.max(45, roundTo5(budgetSec * slShare / 60.0));

        List<CreateSessionRequest> sessions = new ArrayList<>();
        LocalDate monday = week.getStartDate();

        sessions.add(run(monday.plusDays(6), "Sortie longue", null, "EF", slMin,
                slDPlus > 0 ? slDPlus : null, 3, trail ? 5 : 4));
        if (quality != null) {
            sessions.add(run(monday.plusDays(1), quality.title(), quality.detail(), quality.zone(),
                    quality.durationMin(), null, quality.rpeMin(), quality.rpeMax()));
        }

        int efCount = effectiveRuns - 1 - (quality != null ? 1 : 0);
        if (efCount > 0) {
            long qualitySec = quality != null ? quality.durationMin() * 60L : 0;
            long leftoverSec = Math.max(efCount * MIN_EF_MIN * 60L,
                    budgetSec - slMin * 60L - qualitySec);
            int efMin = Math.clamp(roundTo5(leftoverSec / 60.0 / efCount), MIN_EF_MIN, MAX_EF_MIN);
            int hillyDPlus = weekDPlus - slDPlus;
            for (int i = 0; i < Math.min(efCount, EF_DAYS.length); i++) {
                boolean hilly = trail && i == 0 && hillyDPlus >= 100;
                sessions.add(run(monday.plusDays(EF_DAYS[i]),
                        hilly ? "Footing EF vallonné" : "Footing EF", null, "EF",
                        hilly ? efMin + roundTo5(hillyDPlus * pace.climbSecPerMeter() / 60.0) : efMin,
                        hilly ? hillyDPlus : null, 2, 3));
            }
        }

        int effectiveGyms = type == WeekType.TAPER || easyWeek ? Math.min(gyms, 1) : gyms;
        for (int i = 0; i < Math.min(effectiveGyms, GYM_DAYS.length); i++) {
            sessions.add(new CreateSessionRequest(monday.plusDays(GYM_DAYS[i]), 0, Discipline.GYM,
                    "Renfo", null, null, 45, null, null, null, null, null));
        }
        return sessions;
    }

    /** Race week: a shake-out jog the day before, the race itself, one early EF. */
    private List<CreateSessionRequest> raceWeek(TrainingPlan plan, PlanWeek week,
                                                Objective main, PaceModel pace) {
        LocalDate monday = week.getStartDate();
        LocalDate raceDay = main.getDate() != null
                && !main.getDate().isBefore(monday) && main.getDate().isBefore(monday.plusDays(7))
                ? main.getDate()
                : monday.plusDays(6);

        int raceMin;
        if (main.getTargetTimeMin() != null) {
            raceMin = main.getTargetTimeMin();
        } else {
            double km = main.getDistanceKm() != null ? main.getDistanceKm().doubleValue() : 10;
            int dPlus = main.getElevationGainM() != null ? main.getElevationGainM() : 0;
            raceMin = roundTo5((km * pace.secPerKm(Allure.COURSE)
                    + dPlus * pace.climbSecPerMeter()) / 60.0);
        }

        List<CreateSessionRequest> sessions = new ArrayList<>();
        sessions.add(run(monday.plusDays(1), "Footing EF", null, "EF", 30, null, 2, 3));
        sessions.add(run(raceDay.minusDays(1), "Footing déblocage", "15′ EF + 4 lignes", "EF",
                20, null, 2, 3));
        sessions.add(run(raceDay, main.getName(), null, "Course", raceMin,
                main.getElevationGainM(), 8, 10));
        return sessions;
    }

    /** The week's one quality session — phase first, then the plan's focus bias. */
    private QualitySession quality(PlanWeek week, PlanFocus focus, boolean trail) {
        if ("Base".equals(week.getPhase())) {
            return new QualitySession("EF + lignes", "40′ EF + 8 lignes", "EF", 45, 3, 4);
        }
        if (week.getWeekType() == WeekType.TAPER) {
            return new QualitySession("Rappel seuil",
                    "15′ EF + 2×6′ Seuil 60 (récup 2′) + 10′ EF", "Seuil 60", 45, 6, 7);
        }
        if ("Spécifique".equals(week.getPhase())) {
            return trail
                    ? new QualitySession("Côtes",
                            "30′ EF + 10×45″ côte (récup 1′) + 15′ EF", "VMA", 60, 8, 9)
                    : new QualitySession("Allure course",
                            "15′ EF + 3×10′ AC (récup 2′) + 10′ EF", "Tempo/AC", 60, 6, 7);
        }
        return switch (focus) {
            case SPEED -> week.getWeekNumber() % 2 == 0
                    ? new QualitySession("VMA 30/30",
                            "20′ EF + 12×30/30 VMA + 10′ EF", "VMA", 45, 8, 9)
                    : new QualitySession("Seuil 60",
                            "20′ EF + 3×8′ Seuil 60 (récup 2′) + 10′ EF", "Seuil 60", 55, 6, 7);
            case ENDURANCE -> new QualitySession("Tempo",
                    "15′ EF + 2×15′ Tempo (récup 3′) + 10′ EF", "Tempo/AC", 60, 5, 6);
            case MAINTAIN -> new QualitySession("Seuil 60",
                    "20′ EF + 3×8′ Seuil 60 (récup 2′) + 10′ EF", "Seuil 60", 55, 6, 7);
        };
    }

    private static CreateSessionRequest run(LocalDate date, String title, String detail, String zone,
                                            Integer durationMin, Integer elevationM,
                                            Integer rpeMin, Integer rpeMax) {
        return new CreateSessionRequest(date, 0, Discipline.RUN, title, detail, zone,
                durationMin, elevationM, rpeMin, rpeMax, null, null);
    }

    private static int roundTo5(double minutes) {
        return (int) Math.max(5, Math.round(minutes / 5) * 5);
    }
}
