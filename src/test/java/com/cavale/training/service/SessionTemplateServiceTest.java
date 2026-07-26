package com.cavale.training.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.PlanFocus;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.CreateSessionRequest;
import com.cavale.training.pace.PaceModel;
import com.cavale.training.workout.WorkoutParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionTemplateServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 6);

    @Mock
    private TrainingPlanService planService;

    private SessionTemplateService service() {
        return new SessionTemplateService(planService);
    }

    private static TrainingPlan plan(Integer runs, Integer gyms, PlanFocus focus) {
        TrainingPlan plan = new TrainingPlan(USER, "Saison", null,
                MONDAY, MONDAY.plusWeeks(12).plusDays(6));
        plan.updatePreferences(runs, gyms, focus);
        return plan;
    }

    private static PlanWeek week(TrainingPlan plan, WeekType type, String phase,
                                 String km, Integer dPlus) {
        PlanWeek week = new PlanWeek(plan, 2, MONDAY.plusWeeks(1), phase, type,
                new BigDecimal(km), dPlus, null, null);
        ReflectionTestUtils.setField(week, "id", UUID.randomUUID());
        return week;
    }

    private static Objective objective(ObjectiveKind kind) {
        TrainingPlan plan = plan(null, null, null);
        Objective main = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                "Trail X", MONDAY.plusWeeks(12).plusDays(6));
        main.updateKind(kind);
        main.updateIntensity(ObjectiveIntensity.BALANCE);
        return main;
    }

    private List<CreateSessionRequest> fill(TrainingPlan plan, PlanWeek week, Objective main) {
        ArgumentCaptor<CreateSessionRequest> captor = ArgumentCaptor.forClass(CreateSessionRequest.class);
        when(planService.addSession(eq(USER), eq(week.getId()), captor.capture()))
                .thenReturn(mock(PlannedSession.class));
        service().fillWeek(USER, plan, week, main, PaceModel.fallback());
        return captor.getAllValues();
    }

    @Test
    void buildWeek_defaultPrefs_longRunQualityEasyAndGym() {
        TrainingPlan plan = plan(null, null, null); // defaults: 3 runs, 1 gym, MAINTAIN
        PlanWeek week = week(plan, WeekType.BUILD, "Développement", "40", 800);
        List<CreateSessionRequest> sessions = fill(plan, week, objective(ObjectiveKind.TRAIL));

        assertThat(sessions.stream().filter(s -> s.discipline() == Discipline.RUN)).hasSize(3);
        assertThat(sessions.stream().filter(s -> s.discipline() == Discipline.GYM)).hasSize(1);

        CreateSessionRequest sl = sessions.stream()
                .filter(s -> s.title().contains("longue")).findFirst().orElseThrow();
        assertThat(sl.date().getDayOfWeek().getValue()).isEqualTo(7); // Sunday
        assertThat(sl.elevationM()).isEqualTo(480); // 60 % of the week's D+
        CreateSessionRequest quality = sessions.stream()
                .filter(s -> "Seuil 60".equals(s.zone())).findFirst().orElseThrow();
        assertThat(quality.date().getDayOfWeek().getValue()).isEqualTo(2); // Tuesday
        // the long run dominates the week
        assertThat(sl.durationMin()).isGreaterThan(quality.durationMin());
    }

    @Test
    void buildWeek_generatedDetailsParseIntoWorkouts() {
        TrainingPlan plan = plan(4, 0, PlanFocus.SPEED);
        PlanWeek week = week(plan, WeekType.BUILD, "Développement", "45", null);
        List<CreateSessionRequest> sessions = fill(plan, week, objective(ObjectiveKind.ROAD));

        // every generated run must yield a usable workout (validate_plan's rule)
        assertThat(sessions).allSatisfy(s -> {
            if (s.discipline() == Discipline.RUN) {
                assertThat(WorkoutParser.parse(s.detail(), s.zone(), s.durationMin()).nodes())
                        .isNotEmpty();
            }
        });
    }

    @Test
    void buildWeek_neverSchedulesTwoHardDaysBackToBack() {
        TrainingPlan plan = plan(6, 2, PlanFocus.SPEED);
        PlanWeek week = week(plan, WeekType.SHOCK, "Spécifique", "60", 1500);
        List<CreateSessionRequest> sessions = fill(plan, week, objective(ObjectiveKind.TRAIL));

        List<LocalDate> hardDays = sessions.stream()
                .filter(s -> PlanCoachService.isHard(s.discipline(), s.zone(), s.rpeMax()))
                .map(CreateSessionRequest::date)
                .distinct().sorted().toList();
        for (int i = 1; i < hardDays.size(); i++) {
            assertThat(ChronoUnit.DAYS.between(hardDays.get(i - 1), hardDays.get(i)))
                    .isGreaterThan(1);
        }
    }

    @Test
    void deloadWeek_capsRunsAndDropsQuality() {
        TrainingPlan plan = plan(5, 2, PlanFocus.MAINTAIN);
        PlanWeek week = week(plan, WeekType.DELOAD, "Assimilation", "25", 400);
        List<CreateSessionRequest> sessions = fill(plan, week, objective(ObjectiveKind.TRAIL));

        assertThat(sessions.stream().filter(s -> s.discipline() == Discipline.RUN)).hasSize(3);
        assertThat(sessions.stream().filter(s -> s.discipline() == Discipline.GYM)).hasSize(1);
        assertThat(sessions).noneMatch(
                s -> PlanCoachService.isHard(s.discipline(), s.zone(), s.rpeMax()));
    }

    @Test
    void basePhase_qualityIsStridesNotThreshold() {
        TrainingPlan plan = plan(3, 0, PlanFocus.MAINTAIN);
        PlanWeek week = week(plan, WeekType.BUILD, "Base", "35", null);
        List<CreateSessionRequest> sessions = fill(plan, week, objective(ObjectiveKind.ROAD));

        assertThat(sessions).noneMatch(
                s -> PlanCoachService.isHard(s.discipline(), s.zone(), s.rpeMax()));
        assertThat(sessions).anyMatch(s -> "EF + lignes".equals(s.title()));
    }

    @Test
    void raceWeek_holdsTheRaceAndAShakeout() {
        TrainingPlan plan = plan(4, 1, PlanFocus.MAINTAIN);
        Objective main = objective(ObjectiveKind.TRAIL);
        main.updateRaceProfile(new BigDecimal("44.00"), 2000, "Lieu");
        PlanWeek week = new PlanWeek(plan, 13, MONDAY.plusWeeks(12), "Course", WeekType.RACE,
                new BigDecimal("44"), 2000, null, null);
        ReflectionTestUtils.setField(week, "id", UUID.randomUUID());

        ArgumentCaptor<CreateSessionRequest> captor = ArgumentCaptor.forClass(CreateSessionRequest.class);
        when(planService.addSession(eq(USER), eq(week.getId()), captor.capture()))
                .thenReturn(mock(PlannedSession.class));
        service().fillWeek(USER, plan, week, main, PaceModel.fallback());
        List<CreateSessionRequest> sessions = captor.getAllValues();

        CreateSessionRequest race = sessions.stream()
                .filter(s -> "Trail X".equals(s.title())).findFirst().orElseThrow();
        assertThat(race.date()).isEqualTo(main.getDate());
        assertThat(race.elevationM()).isEqualTo(2000);
        assertThat(sessions).anyMatch(s -> s.title().contains("déblocage"));
        assertThat(sessions).noneMatch(s -> s.discipline() == Discipline.GYM);
    }

    @Test
    void singleRunPerWeek_putsTheWholeBudgetInTheLongRun() {
        TrainingPlan plan = plan(1, 0, PlanFocus.MAINTAIN);
        PlanWeek week = week(plan, WeekType.BUILD, "Développement", "20", null);
        List<CreateSessionRequest> sessions = fill(plan, week, objective(ObjectiveKind.ROAD));

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().title()).contains("longue");
        // ~20 km at the 6:30/km fallback EF pace → ~130 min
        assertThat(sessions.getFirst().durationMin()).isBetween(120, 140);
    }
}
