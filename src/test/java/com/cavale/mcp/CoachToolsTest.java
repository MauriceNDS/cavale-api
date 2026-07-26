package com.cavale.mcp;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.athlete.service.AthleteContextService;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.GymTemplate;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseRequest;
import com.cavale.gym.service.ExerciseService;
import com.cavale.gym.service.GymTemplateService;
import com.cavale.training.course.CourseService;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.dto.UpdateSessionRequest;
import com.cavale.training.dto.UpdateWeekRequest;
import com.cavale.training.service.ObjectiveService;
import com.cavale.training.service.PlanCoachService;
import com.cavale.training.service.TrainingPlanService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The MCP tools are thin adapters over the services, so these tests focus on
 * what the adapter itself owns: the athlete-from-JWT resolution, the request
 * mapping, and the P15 hard-day guardrail on session mutations.
 */
@ExtendWith(MockitoExtension.class)
class CoachToolsTest {

    @Mock
    private AthleteContextService contextService;

    @Mock
    private TrainingPlanService planService;

    @Mock
    private ObjectiveService objectiveService;

    @Mock
    private ExerciseService exerciseService;

    @Mock
    private GymTemplateService gymTemplateService;

    @Mock
    private CourseService courseService;

    @Mock
    private PlanCoachService coachService;

    @Mock
    private com.cavale.coach.service.CoachInsightService insightService;

    private static final UUID ATHLETE = UUID.randomUUID();

    private CoachTools tools() {
        return new CoachTools(contextService, planService, objectiveService, exerciseService,
                gymTemplateService, courseService, coachService, insightService);
    }

    @BeforeEach
    void authenticateAsAthlete() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(ATHLETE.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /* ── Fixtures ──────────────────────────────────────────────────────── */

    private static PlanWeek week() {
        TrainingPlan plan = new TrainingPlan(ATHLETE, "SaintéLyon 2026", "sub-8h30",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        PlanWeek week = new PlanWeek(plan, 14, LocalDate.of(2026, 10, 5), null,
                WeekType.BUILD, null, null, null, null);
        ReflectionTestUtils.setField(week, "id", UUID.randomUUID());
        return week;
    }

    private static PlannedSession session(PlanWeek week, LocalDate date, String zone, Integer rpeMax) {
        PlannedSession session = new PlannedSession(week, ATHLETE, date, 0,
                Discipline.RUN, "Séance", null, zone, 60, null, null, rpeMax);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    /* ── update_session guardrail ──────────────────────────────────────── */

    @Test
    void updateSession_rejectsMovingHardSessionNextToHardDay() {
        PlanWeek week = week();
        PlannedSession hard = session(week, LocalDate.of(2026, 10, 7), null, 8);
        PlannedSession neighbour = session(week, LocalDate.of(2026, 10, 9), null, 8);
        when(planService.getOwnedSession(ATHLETE, hard.getId())).thenReturn(hard);
        when(planService.getPlanCalendarForWeek(eq(ATHLETE), eq(week.getId()), any(), any()))
                .thenReturn(List.of(neighbour));

        assertThatThrownBy(() -> tools().updateSession(hard.getId().toString(), "2026-10-10",
                null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hard");

        verify(planService, never()).updateSession(any(), any(), any());
    }

    @Test
    void updateSession_rejectsIntensityChangeCreatingHardClash() {
        PlanWeek week = week();
        PlannedSession easy = session(week, LocalDate.of(2026, 10, 8), "EF", 4);
        PlannedSession neighbour = session(week, LocalDate.of(2026, 10, 9), null, 8);
        when(planService.getOwnedSession(ATHLETE, easy.getId())).thenReturn(easy);
        when(planService.getPlanCalendarForWeek(eq(ATHLETE), eq(week.getId()), any(), any()))
                .thenReturn(List.of(neighbour));

        assertThatThrownBy(() -> tools().updateSession(easy.getId().toString(), null,
                null, null, "VMA", null, null, null, 9, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(planService, never()).updateSession(any(), any(), any());
    }

    @Test
    void updateSession_allowsHardMoveWhenNeighboursAreEasy() {
        PlanWeek week = week();
        PlannedSession hard = session(week, LocalDate.of(2026, 10, 7), null, 8);
        PlannedSession neighbour = session(week, LocalDate.of(2026, 10, 9), "EF", 4);
        when(planService.getOwnedSession(ATHLETE, hard.getId())).thenReturn(hard);
        when(planService.getPlanCalendarForWeek(eq(ATHLETE), eq(week.getId()), any(), any()))
                .thenReturn(List.of(neighbour));
        when(planService.updateSession(eq(ATHLETE), eq(hard.getId()), any())).thenReturn(hard);

        tools().updateSession(hard.getId().toString(), "2026-10-10",
                null, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<UpdateSessionRequest> captor = ArgumentCaptor.forClass(UpdateSessionRequest.class);
        verify(planService).updateSession(eq(ATHLETE), eq(hard.getId()), captor.capture());
        assertThat(captor.getValue().date()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void updateSession_contentOnlyRevisionSkipsGuardrailAndMapsFields() {
        PlanWeek week = week();
        PlannedSession session = session(week, LocalDate.of(2026, 10, 7), "EF", 4);
        when(planService.updateSession(eq(ATHLETE), eq(session.getId()), any())).thenReturn(session);

        // no date/zone/rpeMax change → the guardrail has nothing to check
        tools().updateSession(session.getId().toString(), null,
                "SL 5h", "5h EF vallonné", null, 300, 1800, 3, null, null, "On monte le volume", null);

        verify(planService, never()).getOwnedSession(any(), any());
        ArgumentCaptor<UpdateSessionRequest> captor = ArgumentCaptor.forClass(UpdateSessionRequest.class);
        verify(planService).updateSession(eq(ATHLETE), eq(session.getId()), captor.capture());
        UpdateSessionRequest sent = captor.getValue();
        assertThat(sent.title()).isEqualTo("SL 5h");
        assertThat(sent.detail()).isEqualTo("5h EF vallonné");
        assertThat(sent.durationMin()).isEqualTo(300);
        assertThat(sent.elevationM()).isEqualTo(1800);
        assertThat(sent.rpeMin()).isEqualTo(3);
        assertThat(sent.comment()).isEqualTo("On monte le volume");
    }

    /* ── update_week / deletes ─────────────────────────────────────────── */

    @Test
    void updateWeek_mapsAllFields() {
        PlanWeek week = week();
        when(planService.updateWeek(eq(ATHLETE), eq(week.getId()), any())).thenReturn(week);

        tools().updateWeek(week.getId().toString(), WeekType.SHOCK, "Spécifique",
                70.0, 2000, 600, "Bloc choc");

        ArgumentCaptor<UpdateWeekRequest> captor = ArgumentCaptor.forClass(UpdateWeekRequest.class);
        verify(planService).updateWeek(eq(ATHLETE), eq(week.getId()), captor.capture());
        UpdateWeekRequest sent = captor.getValue();
        assertThat(sent.weekType()).isEqualTo(WeekType.SHOCK);
        assertThat(sent.phase()).isEqualTo("Spécifique");
        assertThat(sent.targetVolumeKm()).isEqualByComparingTo("70.0");
        assertThat(sent.targetElevationM()).isEqualTo(2000);
        assertThat(sent.targetLoadUa()).isEqualTo(600);
        assertThat(sent.focus()).isEqualTo("Bloc choc");
    }

    @Test
    void deleteTools_delegateToServicesAsTheAthlete() {
        UUID sessionId = UUID.randomUUID();
        UUID weekId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        assertThat(tools().deleteSession(sessionId.toString())).isEqualTo("deleted");
        assertThat(tools().deleteWeek(weekId.toString())).isEqualTo("deleted");
        assertThat(tools().deletePlan(planId.toString())).isEqualTo("deleted");

        verify(planService).deleteSession(ATHLETE, sessionId);
        verify(planService).deleteWeek(ATHLETE, weekId);
        verify(planService).deletePlan(ATHLETE, planId);
    }

    /* ── Gym template revision ─────────────────────────────────────────── */

    @Test
    void updateTemplateExercise_delegatesFullReplacement() {
        GymTemplate template = new GymTemplate(ATHLETE, "Force Max", null);
        GymTemplateVariant variant = new GymTemplateVariant(template, "A", null);
        Exercise exercise = new Exercise(ATHLETE, "Squat", ExerciseCategory.FORCE,
                Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        TemplateExercise te = new TemplateExercise(variant, exercise, 0, 5, 5, null, 180, 80, null);
        ReflectionTestUtils.setField(te, "id", UUID.randomUUID());
        when(gymTemplateService.updateExercise(eq(ATHLETE), eq(te.getId()), any())).thenReturn(te);

        tools().updateTemplateExercise(te.getId().toString(), exercise.getId().toString(),
                4, 6, null, 150, 75, "tempo 3-1-1");

        ArgumentCaptor<TemplateExerciseRequest> captor =
                ArgumentCaptor.forClass(TemplateExerciseRequest.class);
        verify(gymTemplateService).updateExercise(eq(ATHLETE), eq(te.getId()), captor.capture());
        TemplateExerciseRequest sent = captor.getValue();
        assertThat(sent.exerciseId()).isEqualTo(exercise.getId());
        assertThat(sent.sets()).isEqualTo(4);
        assertThat(sent.reps()).isEqualTo(6);
        assertThat(sent.restSec()).isEqualTo(150);
        assertThat(sent.intensityPct()).isEqualTo(75);
        assertThat(sent.note()).isEqualTo("tempo 3-1-1");
    }

    @Test
    void removeTemplateExercise_delegates() {
        UUID templateExerciseId = UUID.randomUUID();

        assertThat(tools().removeTemplateExercise(templateExerciseId.toString())).isEqualTo("deleted");

        verify(gymTemplateService).removeExercise(ATHLETE, templateExerciseId);
    }
}
