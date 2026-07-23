package com.cavale.demo;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.ShoePurpose;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.dto.ShoeRequest;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.service.PlanCoachService;
import com.cavale.training.service.ShoeService;
import com.cavale.training.service.TrainingPlanService;

/**
 * Fills a fresh demo account with a full, curated ultra-trail season so a
 * recruiter lands in a lived-in app: eight weeks of past runs (which drive the
 * stats, form/Banister and Course analytics), a SaintéLyon 80 km objective, a
 * periodized plan scaffolded through the real coach, its sessions, and a couple
 * of shoes. Everything goes through the canonical services — no back doors.
 */
@Service
public class DemoSeeder {

    private static final int HISTORY_WEEKS = 8;

    private final TrainingPlanService planService;
    private final PlanCoachService coachService;
    private final ObjectiveRepository objectiveRepository;
    private final ActivityRepository activityRepository;
    private final ShoeService shoeService;

    public DemoSeeder(TrainingPlanService planService, PlanCoachService coachService,
                      ObjectiveRepository objectiveRepository, ActivityRepository activityRepository,
                      ShoeService shoeService) {
        this.planService = planService;
        this.coachService = coachService;
        this.objectiveRepository = objectiveRepository;
        this.activityRepository = activityRepository;
        this.shoeService = shoeService;
    }

    private record DemoShoes(UUID trail, UUID road) {
    }

    @Transactional
    public void seed(UUID userId, LocalDate today) {
        DemoShoes shoes = seedShoes(userId);
        seedHistory(userId, today, shoes);

        LocalDate start = today.minusWeeks(4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate race = today.plusWeeks(8).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        TrainingPlan plan = planService.createPlan(userId, new CreatePlanRequest(
                "SaintéLyon 80 km",
                "Terminer la SaintéLyon 80 km, viser sous 12 h",
                start, race));

        Objective main = objectiveRepository.findByPlanIdAndRole(plan.getId(), ObjectiveRole.MAIN)
                .orElseThrow();
        main.updateKind(ObjectiveKind.TRAIL);
        main.updateIntensity(ObjectiveIntensity.BALANCE);
        main.updateRaceProfile(BigDecimal.valueOf(80), 1500, "Saint-Étienne → Lyon");
        main.updateDate(race);

        // The real coach builds the periodized skeleton off the seeded volume…
        List<PlanWeek> weeks = coachService.scaffold(userId, plan.getId(), today);
        // …then we fill each week with sessions.
        for (PlanWeek week : weeks) {
            seedWeekSessions(userId, week, race, today);
        }
    }

    /* ── past activities (drive every stat) ─────────────────────────────── */

    private void seedHistory(UUID userId, LocalDate today, DemoShoes shoes) {
        List<Activity> activities = new ArrayList<>();
        for (int w = HISTORY_WEEKS; w >= 1; w--) {
            LocalDate monday = today.minusWeeks(w).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int longKm = 17 + (HISTORY_WEEKS - w); // progressive: 17 → 24 km
            // Road pair for the easy/quality runs, trail pair for the long run — so
            // both shoes accrue believable mileage.
            activities.add(run(userId, monday.plusDays(1), 55, 9, 120, 138, 48, "Footing", shoes.road()));
            activities.add(run(userId, monday.plusDays(3), 58, 11, 180, 161, 95, "Séance seuil", shoes.road()));
            activities.add(run(userId, monday.plusDays(6), longKm * 6, longKm, longKm * 32,
                    146, longKm * 7, "Sortie longue trail", shoes.trail()));
        }
        activityRepository.saveAll(activities);
        // Flush so the coach's scaffold sees this volume in the same transaction.
        activityRepository.flush();
    }

    private static Activity run(UUID userId, LocalDate date, int durationMin, int distanceKm,
                                int elevationM, int avgHr, int relativeEffort, String name, UUID shoeId) {
        Activity activity = Activity.manual(userId, date, durationMin,
                BigDecimal.valueOf(distanceKm), elevationM, avgHr, name);
        activity.markDiscipline(Discipline.RUN);
        activity.enrich(null, relativeEffort, avgHr + 12);
        activity.assignShoe(shoeId);
        return activity;
    }

    /* ── planned sessions ───────────────────────────────────────────────── */

    private void seedWeekSessions(UUID userId, PlanWeek week, LocalDate race, LocalDate today) {
        if (week.getWeekType() == WeekType.RACE) {
            addSession(userId, week, race, "Objectif : SaintéLyon 80 km", "80 km / 1500 D+",
                    "SL", 720, 1500, 5, 9, today);
            return;
        }
        LocalDate monday = week.getStartDate();
        boolean quality = week.getWeekType() == WeekType.BUILD || week.getWeekType() == WeekType.SHOCK;
        int longMin = week.getWeekType() == WeekType.TAPER ? 70 : 105;

        addSession(userId, week, monday.plusDays(1), "Footing", "50′ en EF", "EF", 50, 120, 3, 4, today);
        if (quality) {
            addSession(userId, week, monday.plusDays(3),
                    "Seuil", "20′ EF + 5×3′ Seuil (r=90″) + 10′ EF", "Seuil", 60, 200, 6, 8, today);
        } else {
            addSession(userId, week, monday.plusDays(3), "Footing souple", "45′ en EF", "EF", 45, 100, 3, 4, today);
        }
        addSession(userId, week, monday.plusDays(6),
                "Sortie longue", longMin + "′ vallonné en EF", "SL", longMin, longMin * 6, 4, 6, today);
    }

    private void addSession(UUID userId, PlanWeek week, LocalDate date, String title, String detail,
                            String zone, int durationMin, int elevationM, int rpeMin, int rpeMax,
                            LocalDate today) {
        PlannedSession session = planService.addSession(userId, week.getId(), new CreateSessionRequest(
                date, 0, Discipline.RUN, title, detail, zone, durationMin, elevationM, rpeMin, rpeMax,
                null, null));
        if (date.isBefore(today)) {
            session.updateStatus(SessionStatus.DONE);
        }
    }

    /* ── gear ───────────────────────────────────────────────────────────── */

    private DemoShoes seedShoes(UUID userId) {
        UUID trail = shoeService.create(userId,
                new ShoeRequest("Speedgoat 5", "Hoka", "#14B4C8", "#F97316", ShoePurpose.TRAIL, 800, false, true)).id();
        UUID road = shoeService.create(userId,
                new ShoeRequest("Pegasus 40", "Nike", "#111111", null, ShoePurpose.ROAD, 700, false, false)).id();
        return new DemoShoes(trail, road);
    }
}
