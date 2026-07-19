package com.cavale.gym.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.GymTemplate;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.dto.WorkoutDtos.FinishWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.LogSetRequest;
import com.cavale.gym.dto.WorkoutDtos.SetLogResponse;
import com.cavale.gym.dto.WorkoutDtos.StartWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.WorkoutDetailResponse;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;
import com.cavale.gym.repository.WorkoutLogRepository;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PerceivedEffort;
import com.cavale.training.domain.PlanWeek;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.domain.WeekType;
import com.cavale.training.repository.PlannedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private WorkoutLogRepository workoutLogRepository;

    @Mock
    private SetLogRepository setLogRepository;

    @Mock
    private TemplateExerciseRepository templateExerciseRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    @Mock
    private GymTemplateService templateService;

    @Mock
    private ExerciseService exerciseService;

    private WorkoutService service() {
        return new WorkoutService(workoutLogRepository, setLogRepository,
                templateExerciseRepository, sessionRepository, templateService, exerciseService);
    }

    /* ── Fixtures ─────────────────────────────────────────────────────── */

    private static GymTemplateVariant variant() {
        GymTemplate template = new GymTemplate(USER, "Force Max", null);
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        GymTemplateVariant variant = new GymTemplateVariant(template, "A", null);
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        return variant;
    }

    private static Exercise squat() {
        Exercise exercise = new Exercise(USER, "Squat", ExerciseCategory.FORCE,
                Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        return exercise;
    }

    private static PlannedSession gymSession(GymTemplateVariant variant) {
        LocalDate date = LocalDate.of(2026, 7, 14);
        TrainingPlan plan = new TrainingPlan(USER, "Plan", null, date.minusDays(7), date.plusDays(60));
        PlanWeek week = new PlanWeek(plan, 1, date, null, WeekType.BUILD, null, null, null, null);
        PlannedSession session = new PlannedSession(week, USER, date, 0, Discipline.GYM,
                "Force Max A", null, null, 60, null, null, null);
        session.linkTemplateVariant(variant);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private static WorkoutLog inProgress(GymTemplateVariant variant, PlannedSession session) {
        WorkoutLog log = new WorkoutLog(USER, session, variant, "Force Max · A",
                Instant.now().minusSeconds(1800));
        ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
        return log;
    }

    /* ── Start / resume ───────────────────────────────────────────────── */

    @Test
    void start_fromSession_usesItsVariantAndSnapshotsTheName() {
        GymTemplateVariant variant = variant();
        PlannedSession session = gymSession(variant);
        when(workoutLogRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER,
                WorkoutStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(workoutLogRepository.findBySessionId(session.getId())).thenReturn(Optional.empty());
        when(workoutLogRepository.save(any(WorkoutLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of());
        when(setLogRepository.findByWorkoutLogIdOrderByPositionAscSetNumberAsc(any()))
                .thenReturn(List.of());

        WorkoutDetailResponse detail = service().start(USER,
                new StartWorkoutRequest(session.getId(), null));

        assertThat(detail.log().templateName()).isEqualTo("Force Max · A");
        assertThat(detail.log().status()).isEqualTo(WorkoutStatus.IN_PROGRESS);
    }

    @Test
    void start_resumesTheWorkoutAlreadyInProgress() {
        GymTemplateVariant variant = variant();
        WorkoutLog running = inProgress(variant, null);
        when(workoutLogRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER,
                WorkoutStatus.IN_PROGRESS)).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of());
        when(setLogRepository.findByWorkoutLogIdOrderByPositionAscSetNumberAsc(running.getId()))
                .thenReturn(List.of());

        WorkoutDetailResponse detail = service().start(USER, new StartWorkoutRequest(null, null));

        assertThat(detail.log().id()).isEqualTo(running.getId());
        verify(workoutLogRepository, never()).save(any());
    }

    @Test
    void start_withoutAnyVariant_isRejected() {
        when(workoutLogRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER,
                WorkoutStatus.IN_PROGRESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().start(USER, new StartWorkoutRequest(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("programme");
    }

    @Test
    void start_rejectsSessionsAlreadyLogged() {
        GymTemplateVariant variant = variant();
        PlannedSession session = gymSession(variant);
        when(workoutLogRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER,
                WorkoutStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(workoutLogRepository.findBySessionId(session.getId()))
                .thenReturn(Optional.of(inProgress(variant, session)));

        assertThatThrownBy(() -> service().start(USER, new StartWorkoutRequest(session.getId(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà un entraînement");
    }

    /* ── Prefill & records ────────────────────────────────────────────── */

    @Test
    void detail_carriesLastSetsAndRecordPerBlock() {
        GymTemplateVariant variant = variant();
        Exercise squat = squat();
        TemplateExercise te = new TemplateExercise(variant, squat, 0, 3, 6, null, 180, 75, null);
        ReflectionTestUtils.setField(te, "id", UUID.randomUUID());
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(te));
        WorkoutLog previous = inProgress(variant, null);
        SetLog lastSet = new SetLog(previous, squat, 0, 1, 6, new BigDecimal("85.0"), null);
        when(setLogRepository.findLastWorkoutSets(USER, squat.getId())).thenReturn(List.of(lastSet));
        when(setLogRepository.findRecordWeight(USER, squat.getId(), 6))
                .thenReturn(Optional.of(new BigDecimal("92.5")));
        when(templateService.getAlternatives(te.getId())).thenReturn(List.of());
        when(setLogRepository.findByWorkoutLogIdOrderByPositionAscSetNumberAsc(running.getId()))
                .thenReturn(List.of());

        WorkoutDetailResponse detail = service().get(USER, running.getId());

        assertThat(detail.blocks()).hasSize(1);
        var block = detail.blocks().getFirst();
        assertThat(block.lastSets()).extracting(SetLogResponse::weightKg)
                .containsExactly(new BigDecimal("85.0"));
        assertThat(block.recordWeightKg()).isEqualByComparingTo("92.5");
        assertThat(block.targetReps()).isEqualTo(6);
    }

    /* ── Logging sets ─────────────────────────────────────────────────── */

    @Test
    void logSet_createsThenUpdatesOnSameSetNumber() {
        GymTemplateVariant variant = variant();
        WorkoutLog running = inProgress(variant, null);
        Exercise squat = squat();
        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(exerciseService.getOwned(USER, squat.getId())).thenReturn(squat);
        when(setLogRepository.findByWorkoutLogIdAndExerciseIdAndSetNumber(running.getId(),
                squat.getId(), 1)).thenReturn(Optional.empty());
        when(setLogRepository.save(any(SetLog.class))).thenAnswer(inv -> inv.getArgument(0));

        SetLogResponse first = service().logSet(USER, running.getId(),
                new LogSetRequest(squat.getId(), 0, 1, 6, new BigDecimal("85.0"), null));
        assertThat(first.weightKg()).isEqualByComparingTo("85.0");

        SetLog existing = new SetLog(running, squat, 0, 1, 6, new BigDecimal("85.0"), null);
        when(setLogRepository.findByWorkoutLogIdAndExerciseIdAndSetNumber(running.getId(),
                squat.getId(), 1)).thenReturn(Optional.of(existing));

        SetLogResponse corrected = service().logSet(USER, running.getId(),
                new LogSetRequest(squat.getId(), 0, 1, 6, new BigDecimal("87.5"), null));

        assertThat(corrected.weightKg()).isEqualByComparingTo("87.5");
        assertThat(existing.getWeightKg()).isEqualByComparingTo("87.5");
    }

    @Test
    void logSet_rejectsFinishedWorkouts() {
        WorkoutLog done = inProgress(variant(), null);
        done.finish(45, PerceivedEffort.COMME_PREVU, false, null);
        when(workoutLogRepository.findById(done.getId())).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service().logSet(USER, done.getId(),
                new LogSetRequest(UUID.randomUUID(), 0, 1, 6, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminé");
    }

    /* ── Finish ───────────────────────────────────────────────────────── */

    @Test
    void finish_validatesThePlannedSessionAndDefaultsDuration() {
        GymTemplateVariant variant = variant();
        PlannedSession session = gymSession(variant);
        WorkoutLog running = inProgress(variant, session);
        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(setLogRepository.findByWorkoutLogIdOrderByPositionAscSetNumberAsc(running.getId()))
                .thenReturn(List.of());

        var response = service().finish(USER, running.getId(),
                new FinishWorkoutRequest(null, PerceivedEffort.DIFFICILE, true, "genou droit sensible"));

        assertThat(response.status()).isEqualTo(WorkoutStatus.FINISHED);
        assertThat(response.durationMin()).isEqualTo(30); // started 30 min ago
        assertThat(response.painFlag()).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DONE);
    }

    @Test
    void abandon_deletesOnlyInProgressWorkouts() {
        WorkoutLog running = inProgress(variant(), null);
        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        service().abandon(USER, running.getId());
        verify(workoutLogRepository).delete(running);

        WorkoutLog done = inProgress(variant(), null);
        done.finish(45, PerceivedEffort.COMME_PREVU, false, null);
        when(workoutLogRepository.findById(done.getId())).thenReturn(Optional.of(done));
        assertThatThrownBy(() -> service().abandon(USER, done.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
