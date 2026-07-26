package com.cavale.training.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.athlete.dto.RunningStatsResponse;
import com.cavale.athlete.dto.RunningStatsResponse.Acwr;
import com.cavale.athlete.dto.RunningStatsResponse.AcwrZone;
import com.cavale.athlete.dto.RunningStatsResponse.WeekVolume;
import com.cavale.athlete.service.RunningStatsService;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreateWeekRequest;
import com.cavale.training.dto.PlanRealignResponse;
import com.cavale.training.dto.PlanRealignResponse.Tier;
import com.cavale.training.dto.PlanValidationResponse;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.PlanWeekRepository;
import com.cavale.training.repository.PlannedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanCoachServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13); // a Monday

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private PlanWeekRepository weekRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    @Mock
    private com.cavale.training.repository.ActivityRepository activityRepository;

    @Mock
    private TrainingPlanService planService;

    @Mock
    private RunningStatsService runningStatsService;

    @Mock
    private SessionTemplateService templateService;

    @Mock
    private com.cavale.training.pace.PaceModelService paceModelService;

    private PlanCoachService service() {
        return new PlanCoachService(objectiveRepository, weekRepository, sessionRepository,
                activityRepository, planService, runningStatsService, templateService,
                paceModelService);
    }

    private static RunningStatsResponse statsWithWeeklyKm(String km) {
        WeekVolume week = new WeekVolume(LocalDate.of(2026, 6, 29), new BigDecimal(km), 1000, 300,
                new BigDecimal("60"), 4);
        return new RunningStatsResponse(List.of(), List.of(),
                new Acwr(1.0, 50, 50, AcwrZone.OPTIMAL), List.of(week), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, List.of(), null, List.of(), List.of(), null);
    }

    private static PlanWeek week(TrainingPlan plan, int number, LocalDate monday, WeekType type) {
        PlanWeek week = new PlanWeek(plan, number, monday, type.name(), type, null, null, null, null);
        ReflectionTestUtils.setField(week, "id", UUID.randomUUID());
        return week;
    }

    /* ── P13: scaffold ─────────────────────────────────────────────────── */

    @Test
    void scaffold_periodizesWithProgressiveLoadDeloadTaperAndRace() {
        TrainingPlan plan = new TrainingPlan(USER, "Saison", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 9, 28));
        Objective main = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE, "Trail X",
                LocalDate.of(2026, 9, 21)); // 12 weeks out, a Monday
        main.updateKind(ObjectiveKind.TRAIL);
        main.updateIntensity(ObjectiveIntensity.PERFORMANCE);
        main.updateRaceProfile(new BigDecimal("44.00"), 2000, "Lieu");

        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(PLAN)).thenReturn(List.of());
        when(objectiveRepository.findByPlanIdAndRole(PLAN, ObjectiveRole.MAIN)).thenReturn(Optional.of(main));
        when(runningStatsService.getStats(eq(USER), any())).thenReturn(statsWithWeeklyKm("50"));
        ArgumentCaptor<CreateWeekRequest> captor = ArgumentCaptor.forClass(CreateWeekRequest.class);
        when(planService.addWeek(eq(USER), eq(PLAN), captor.capture())).thenReturn(mock(PlanWeek.class));

        service().scaffold(USER, PLAN, TODAY);

        List<CreateWeekRequest> weeks = captor.getAllValues();
        assertThat(weeks).hasSize(12);
        assertThat(weeks.getLast().weekType()).isEqualTo(WeekType.RACE);
        assertThat(weeks).anyMatch(w -> w.weekType() == WeekType.TAPER);
        assertThat(weeks).anyMatch(w -> w.weekType() == WeekType.DELOAD);
        assertThat(weeks).anyMatch(w -> w.weekType() == WeekType.SHOCK);
        // progressive overload: the peak week out-loads the first base week
        BigDecimal firstBase = weeks.getFirst().targetVolumeKm();
        BigDecimal peak = weeks.stream().filter(w -> w.weekType() == WeekType.SHOCK)
                .findFirst().orElseThrow().targetVolumeKm();
        assertThat(peak).isGreaterThan(firstBase);
        // trail objective → weeks carry a D+ target
        assertThat(weeks.getFirst().targetElevationM()).isPositive();
        // Mondays, 1-based, in order
        assertThat(weeks.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(weeks.getFirst().weekNumber()).isEqualTo(1);
    }

    @Test
    void scaffold_fitnessSeasonRollsBuildDeloadWithoutTaperOrRace() {
        TrainingPlan plan = new TrainingPlan(USER, "Remise en forme", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 9, 28));
        Objective main = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.FITNESS,
                "Progresser", LocalDate.of(2026, 9, 28));
        main.updateKind(ObjectiveKind.ROAD);

        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(PLAN)).thenReturn(List.of());
        when(objectiveRepository.findByPlanIdAndRole(PLAN, ObjectiveRole.MAIN)).thenReturn(Optional.of(main));
        when(runningStatsService.getStats(eq(USER), any())).thenReturn(statsWithWeeklyKm("40"));
        ArgumentCaptor<CreateWeekRequest> captor = ArgumentCaptor.forClass(CreateWeekRequest.class);
        when(planService.addWeek(eq(USER), eq(PLAN), captor.capture())).thenReturn(mock(PlanWeek.class));

        service().scaffold(USER, PLAN, TODAY);

        List<CreateWeekRequest> weeks = captor.getAllValues();
        assertThat(weeks).noneMatch(w -> w.weekType() == WeekType.TAPER);
        assertThat(weeks).noneMatch(w -> w.weekType() == WeekType.RACE);
        assertThat(weeks).anyMatch(w -> w.weekType() == WeekType.DELOAD);
        assertThat(weeks).anyMatch(w -> w.weekType() == WeekType.BUILD);
        // road objective → no D+ targets
        assertThat(weeks.getFirst().targetElevationM()).isNull();
    }

    @Test
    void scaffold_recoverySeasonKeepsEveryWeekEasyAndCapped() {
        TrainingPlan plan = new TrainingPlan(USER, "Retour de blessure", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 31));
        Objective main = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RECOVERY,
                "Reprise", LocalDate.of(2026, 8, 31));
        main.updateKind(ObjectiveKind.ROAD);

        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(PLAN)).thenReturn(List.of());
        when(objectiveRepository.findByPlanIdAndRole(PLAN, ObjectiveRole.MAIN)).thenReturn(Optional.of(main));
        when(runningStatsService.getStats(eq(USER), any())).thenReturn(statsWithWeeklyKm("40"));
        ArgumentCaptor<CreateWeekRequest> captor = ArgumentCaptor.forClass(CreateWeekRequest.class);
        when(planService.addWeek(eq(USER), eq(PLAN), captor.capture())).thenReturn(mock(PlanWeek.class));

        service().scaffold(USER, PLAN, TODAY);

        List<CreateWeekRequest> weeks = captor.getAllValues();
        assertThat(weeks).allMatch(w -> w.weekType() == WeekType.RECOVERY);
        // capped: every week stays below the athlete's normal volume
        assertThat(weeks).allSatisfy(w ->
                assertThat(w.targetVolumeKm()).isLessThan(new BigDecimal("40")));
    }

    @Test
    void scaffold_generalSeasonMaintainsSteadyVolume() {
        TrainingPlan plan = new TrainingPlan(USER, "Entretien", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 31));
        Objective main = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.GENERAL,
                "Courir tranquille", null);
        main.updateKind(ObjectiveKind.TRAIL);

        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(PLAN)).thenReturn(List.of());
        when(objectiveRepository.findByPlanIdAndRole(PLAN, ObjectiveRole.MAIN)).thenReturn(Optional.of(main));
        when(runningStatsService.getStats(eq(USER), any())).thenReturn(statsWithWeeklyKm("40"));
        ArgumentCaptor<CreateWeekRequest> captor = ArgumentCaptor.forClass(CreateWeekRequest.class);
        when(planService.addWeek(eq(USER), eq(PLAN), captor.capture())).thenReturn(mock(PlanWeek.class));

        service().scaffold(USER, PLAN, TODAY);

        List<CreateWeekRequest> weeks = captor.getAllValues();
        assertThat(weeks).noneMatch(w -> w.weekType() == WeekType.TAPER);
        // steady: all non-deload weeks share the same target
        List<BigDecimal> normal = weeks.stream()
                .filter(w -> w.weekType() == WeekType.BUILD)
                .map(CreateWeekRequest::targetVolumeKm)
                .distinct()
                .toList();
        assertThat(normal).hasSize(1);
        assertThat(weeks).anyMatch(w -> w.weekType() == WeekType.DELOAD);
    }

    @Test
    void scaffold_withFillGeneratesSessionsForEveryWeek() {
        TrainingPlan plan = new TrainingPlan(USER, "Saison", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 9, 28));
        Objective main = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE, "Trail X",
                LocalDate.of(2026, 9, 21));

        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(PLAN)).thenReturn(List.of());
        when(objectiveRepository.findByPlanIdAndRole(PLAN, ObjectiveRole.MAIN)).thenReturn(Optional.of(main));
        when(runningStatsService.getStats(eq(USER), any())).thenReturn(statsWithWeeklyKm("50"));
        when(paceModelService.modelFor(USER)).thenReturn(com.cavale.training.pace.PaceModel.fallback());
        when(planService.addWeek(eq(USER), eq(PLAN), any())).thenReturn(mock(PlanWeek.class));

        List<PlanWeek> created = service().scaffold(USER, PLAN, TODAY, true);

        org.mockito.Mockito.verify(templateService, org.mockito.Mockito.times(created.size()))
                .fillWeek(eq(USER), eq(plan), any(), eq(main), any());
    }

    /* ── P13: validate ─────────────────────────────────────────────────── */

    @Test
    void validate_flagsEmptyWeeksAndBackToBackHardDays() {
        TrainingPlan plan = new TrainingPlan(USER, "Saison", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 30));
        PlanWeek w1 = week(plan, 1, LocalDate.of(2026, 7, 6), WeekType.BUILD);
        PlanWeek w2 = week(plan, 2, LocalDate.of(2026, 7, 13), WeekType.BUILD);
        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);
        when(weekRepository.findByPlanIdOrderByWeekNumber(PLAN)).thenReturn(List.of(w1, w2));

        // two hard days back-to-back in week 1; week 2 has no session
        PlannedSession vma = new PlannedSession(w1, USER, LocalDate.of(2026, 7, 7), 0, Discipline.RUN,
                "VMA", null, "VMA", 60, null, null, 8);
        PlannedSession seuil = new PlannedSession(w1, USER, LocalDate.of(2026, 7, 8), 0, Discipline.RUN,
                "Seuil", null, "Seuil 60", 60, null, null, 7);
        when(sessionRepository.findByWeekPlanId(PLAN)).thenReturn(List.of(vma, seuil));

        PlanValidationResponse result = service().validate(USER, PLAN);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anySatisfy(i -> assertThat(i).contains("back-to-back"));
        assertThat(result.issues()).anySatisfy(i -> assertThat(i).contains("Week 2"));
    }

    /* ── P14: realign ──────────────────────────────────────────────────── */

    @Test
    void realign_rebuildsForwardAfterMissedSessions() {
        TrainingPlan plan = new TrainingPlan(USER, "Saison", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 9, 28));
        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);

        PlanWeek pastWeek = week(plan, 1, LocalDate.of(2026, 7, 6), WeekType.BUILD);
        // three run sessions earlier this week, still PLANNED and now in the past → missed
        List<PlannedSession> missed = List.of(
                new PlannedSession(pastWeek, USER, TODAY.minusDays(4), 0, Discipline.RUN, "EF", null, "EF", 60, null, null, null),
                new PlannedSession(pastWeek, USER, TODAY.minusDays(3), 0, Discipline.RUN, "Seuil", null, "Seuil 60", 60, null, null, 7),
                new PlannedSession(pastWeek, USER, TODAY.minusDays(2), 0, Discipline.RUN, "SL", null, "SL", 60, null, null, null));
        when(sessionRepository.findByWeekPlanId(PLAN)).thenReturn(missed);

        PlanWeek up1 = week(plan, 2, TODAY.plusWeeks(1), WeekType.BUILD);
        PlanWeek up2 = week(plan, 3, TODAY.plusWeeks(2), WeekType.BUILD);
        when(weekRepository.findByPlanIdOrderByWeekNumber(PLAN)).thenReturn(List.of(pastWeek, up1, up2));
        when(activityRepository.findByUserIdAndSessionIsNullAndDateBetween(eq(USER), any(), any()))
                .thenReturn(List.of());

        PlanRealignResponse result = service().realign(USER, PLAN, TODAY);

        assertThat(result.tier()).isEqualTo(Tier.REBUILD);
        assertThat(result.missedRunSessions()).isEqualTo(3);
        assertThat(result.missedVolumeKm()).isEqualByComparingTo("30"); // 3 × 60 min / 6
        // ~60 % added back across the two upcoming weeks, not the whole miss
        assertThat(result.redistribution()).hasSize(2);
        assertThat(result.redistribution().getFirst().addKm()).isEqualByComparingTo("9"); // 30×0.6/2
    }

    @Test
    void realign_countsUnplannedHikesAsExtraLoad() {
        TrainingPlan plan = new TrainingPlan(USER, "Saison", null,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 9, 28));
        when(planService.getOwnedPlan(USER, PLAN)).thenReturn(plan);
        when(sessionRepository.findByWeekPlanId(PLAN)).thenReturn(List.of());

        // an 18 km / 1200 m trek synced from Strava, attached to no session
        com.cavale.training.domain.Activity hike = com.cavale.training.domain.Activity
                .stravaHistory(USER, TODAY.minusDays(2), 240, new BigDecimal("18.00"), 1200,
                        110, "Trek Chamonix", 99L);
        hike.markDiscipline(Discipline.HIKE);
        when(activityRepository.findByUserIdAndSessionIsNullAndDateBetween(eq(USER), any(), any()))
                .thenReturn(List.of(hike));

        PlanRealignResponse result = service().realign(USER, PLAN, TODAY);

        assertThat(result.unplannedHikes()).isEqualTo(1);
        assertThat(result.unplannedHikeKmEffort()).isEqualByComparingTo("30"); // 18 + 1200/100
        assertThat(result.guidance()).contains("km-effort");
    }

    /* ── Shared: hard-session classification ───────────────────────────── */

    @Test
    void isHard_classifiesByZoneAndRpe() {
        assertThat(PlanCoachService.isHard(Discipline.RUN, "VMA", null)).isTrue();
        assertThat(PlanCoachService.isHard(Discipline.RUN, "Seuil 60", null)).isTrue();
        assertThat(PlanCoachService.isHard(Discipline.RUN, "EF", 8)).isTrue();      // high RPE
        assertThat(PlanCoachService.isHard(Discipline.RUN, "EF", 4)).isFalse();     // easy endurance
        assertThat(PlanCoachService.isHard(Discipline.CROSS, "VMA", 9)).isFalse();  // a bike is never "hard run"
    }
}
