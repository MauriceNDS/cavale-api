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
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.domain.TemplateExerciseAlternative;
import com.cavale.gym.domain.WorkoutBlockOverride;
import com.cavale.gym.domain.WorkoutExtraBlock;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.dto.WorkoutDtos;
import com.cavale.gym.dto.WorkoutDtos.AddExtraBlockRequest;
import com.cavale.gym.dto.WorkoutDtos.FinishWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.LogSetRequest;
import com.cavale.gym.dto.WorkoutDtos.SetLogResponse;
import com.cavale.gym.dto.WorkoutDtos.StartWorkoutRequest;
import com.cavale.gym.dto.WorkoutDtos.SwapBlockRequest;
import com.cavale.gym.dto.WorkoutDtos.WorkoutBlockResponse;
import com.cavale.gym.dto.WorkoutDtos.WorkoutDetailResponse;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;
import com.cavale.gym.repository.WorkoutBlockOverrideRepository;
import com.cavale.gym.repository.WorkoutExtraBlockRepository;
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
    private WorkoutBlockOverrideRepository overrideRepository;

    @Mock
    private WorkoutExtraBlockRepository extraBlockRepository;

    @Mock
    private PlannedSessionRepository sessionRepository;

    @Mock
    private GymTemplateService templateService;

    @Mock
    private ExerciseService exerciseService;

    private WorkoutService service() {
        return new WorkoutService(workoutLogRepository, setLogRepository,
                templateExerciseRepository, overrideRepository, extraBlockRepository,
                sessionRepository, templateService, exerciseService);
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

    private static Exercise exercise(String name) {
        Exercise exercise = new Exercise(USER, name, ExerciseCategory.FORCE,
                Equipment.MACHINE, ExerciseMeasure.WEIGHT_REPS);
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        return exercise;
    }

    private static TemplateExercise prescription(GymTemplateVariant variant, Exercise exercise) {
        TemplateExercise te = new TemplateExercise(variant, exercise, 0, 3, 6, null, 180, null, null);
        ReflectionTestUtils.setField(te, "id", UUID.randomUUID());
        return te;
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
                new LogSetRequest(squat.getId(), 0, 1, 6, new BigDecimal("85.0"), null, null, null));
        assertThat(first.weightKg()).isEqualByComparingTo("85.0");

        SetLog existing = new SetLog(running, squat, 0, 1, 6, new BigDecimal("85.0"), null);
        when(setLogRepository.findByWorkoutLogIdAndExerciseIdAndSetNumber(running.getId(),
                squat.getId(), 1)).thenReturn(Optional.of(existing));

        SetLogResponse corrected = service().logSet(USER, running.getId(),
                new LogSetRequest(squat.getId(), 0, 1, 6, new BigDecimal("87.5"), null, null, null));

        assertThat(corrected.weightKg()).isEqualByComparingTo("87.5");
        assertThat(existing.getWeightKg()).isEqualByComparingTo("87.5");
    }

    @Test
    void logSet_defaultsToAWorkingSet_andKeepsTheRatingWhenCorrected() {
        GymTemplateVariant variant = variant();
        Exercise squat = squat();
        WorkoutLog running = inProgress(variant, null);
        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(exerciseService.getOwned(USER, squat.getId())).thenReturn(squat);

        SetLog existing = new SetLog(running, squat, 0, 1, 6, new BigDecimal("85.0"), null);
        existing.rateReserve(2);
        when(setLogRepository.findByWorkoutLogIdAndExerciseIdAndSetNumber(running.getId(),
                squat.getId(), 1)).thenReturn(Optional.of(existing));

        // the runner omits warmup for an ordinary set, and re-ticks without a rating
        SetLogResponse corrected = service().logSet(USER, running.getId(),
                new LogSetRequest(squat.getId(), 0, 1, 6, new BigDecimal("90.0"), null, null, null));

        assertThat(corrected.warmup()).isFalse();
        assertThat(corrected.rir()).isEqualTo(2); // correcting the load never erases how it felt
    }

    @Test
    void logSet_rejectsFinishedWorkouts() {
        WorkoutLog done = inProgress(variant(), null);
        done.finish(45, PerceivedEffort.COMME_PREVU, false, null);
        when(workoutLogRepository.findById(done.getId())).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service().logSet(USER, done.getId(),
                new LogSetRequest(UUID.randomUUID(), 0, 1, 6, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminé");
    }

    /* ── Mid-workout deviations ───────────────────────────────────────── */

    @Test
    void swapBlock_toAnAlternative_prefillsFollowTheReplacement() {
        GymTemplateVariant variant = variant();
        Exercise squat = squat();
        Exercise press = exercise("Presse");
        TemplateExercise te = prescription(variant, squat);
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(exerciseService.getOwned(USER, press.getId())).thenReturn(press);
        when(templateService.getAlternatives(te.getId()))
                .thenReturn(List.of(new TemplateExerciseAlternative(te, press, 0)));
        when(overrideRepository.findByWorkoutLogIdAndTemplateExerciseId(running.getId(), te.getId()))
                .thenReturn(Optional.empty());
        when(overrideRepository.save(any(WorkoutBlockOverride.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(setLogRepository.findLastWorkoutSets(USER, press.getId())).thenReturn(List.of());
        when(setLogRepository.findRecordWeight(USER, press.getId(), 6)).thenReturn(Optional.empty());

        WorkoutBlockResponse block = service().swapBlock(USER, running.getId(), te.getId(),
                new SwapBlockRequest(press.getId()));

        assertThat(block.exercise().name()).isEqualTo("Presse");
        assertThat(block.swappedFrom().name()).isEqualTo("Squat");
        assertThat(block.skipped()).isFalse();
        // prefill and record were looked up for the replacement, not the prescription
        verify(setLogRepository).findLastWorkoutSets(USER, press.getId());
        verify(setLogRepository, never()).findLastWorkoutSets(USER, squat.getId());
    }

    @Test
    void swapBlock_acceptsAnyOwnedExercise_beyondDeclaredAlternatives() {
        GymTemplateVariant variant = variant();
        TemplateExercise te = prescription(variant, squat());
        Exercise stranger = exercise("Presse à cuisses");
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(exerciseService.getOwned(USER, stranger.getId())).thenReturn(stranger);
        when(overrideRepository.findByWorkoutLogIdAndTemplateExerciseId(running.getId(), te.getId()))
                .thenReturn(Optional.empty());
        when(overrideRepository.save(any(WorkoutBlockOverride.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WorkoutBlockResponse block = service().swapBlock(USER, running.getId(), te.getId(),
                new SwapBlockRequest(stranger.getId()));

        assertThat(block.exercise().name()).isEqualTo("Presse à cuisses");
        assertThat(block.swappedFrom().name()).isEqualTo("Squat");
    }

    @Test
    void swapBlock_rejectsAnExerciseAlreadyUsedByAnotherBlock() {
        GymTemplateVariant variant = variant();
        Exercise squat = squat();
        Exercise press = exercise("Presse");
        TemplateExercise te = prescription(variant, squat);
        TemplateExercise other = prescription(variant, press);
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(exerciseService.getOwned(USER, press.getId())).thenReturn(press);
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(te, other));

        assertThatThrownBy(() -> service().swapBlock(USER, running.getId(), te.getId(),
                new SwapBlockRequest(press.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fait déjà partie");
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void adjustBlockSets_toZero_persistsTheOverride() {
        GymTemplateVariant variant = variant();
        TemplateExercise te = prescription(variant, squat()); // prescribes 3 sets
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(overrideRepository.findByWorkoutLogIdAndTemplateExerciseId(running.getId(), te.getId()))
                .thenReturn(Optional.empty());
        when(overrideRepository.save(any(WorkoutBlockOverride.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WorkoutBlockResponse block = service().adjustBlockSets(USER, running.getId(), te.getId(),
                new WorkoutDtos.AdjustSetsRequest(0));

        assertThat(block.sets()).isZero();
        assertThat(block.prescribedSets()).isEqualTo(3);
        verify(overrideRepository).save(any(WorkoutBlockOverride.class));
    }

    @Test
    void adjustBlockSets_backToPrescription_prunesTheOverride() {
        GymTemplateVariant variant = variant();
        TemplateExercise te = prescription(variant, squat()); // prescribes 3 sets
        WorkoutLog running = inProgress(variant, null);
        WorkoutBlockOverride override = new WorkoutBlockOverride(running, te);
        override.adjustSets(1);
        ReflectionTestUtils.setField(override, "id", UUID.randomUUID());

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(overrideRepository.findByWorkoutLogIdAndTemplateExerciseId(running.getId(), te.getId()))
                .thenReturn(Optional.of(override));

        WorkoutBlockResponse block = service().adjustBlockSets(USER, running.getId(), te.getId(),
                new WorkoutDtos.AdjustSetsRequest(3));

        assertThat(block.sets()).isEqualTo(3);
        verify(overrideRepository).delete(override);
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void oneGroupHoldingEverything_readsBackAsACircuit() {
        GymTemplateVariant variant = variant();
        TemplateExercise squat = prescription(variant, squat());   // 3 sets, rest 180
        TemplateExercise planche = prescription(variant, exercise("Planche"));
        ReflectionTestUtils.setField(planche, "position", 1);
        squat.assignGroup("A");
        planche.assignGroup("A");
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(squat, planche));

        WorkoutDetailResponse detail = service().get(USER, running.getId());

        // rounds = the longest member's sets; the rest is the one after the last member
        assertThat(detail.circuitLoops()).isEqualTo(3);
        assertThat(detail.circuitRestSec()).isEqualTo(180);
        assertThat(detail.blocks()).extracting(WorkoutBlockResponse::groupKey)
                .containsExactly("A", "A");
    }

    @Test
    void aSupersetIsNotACircuit_whenOtherBlocksStandAlone() {
        GymTemplateVariant variant = variant();
        TemplateExercise squat = prescription(variant, squat());
        TemplateExercise planche = prescription(variant, exercise("Planche"));
        ReflectionTestUtils.setField(planche, "position", 1);
        TemplateExercise press = prescription(variant, exercise("Presse"));
        ReflectionTestUtils.setField(press, "position", 2);
        squat.assignGroup("A");
        planche.assignGroup("A");
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(squat, planche, press));

        WorkoutDetailResponse detail = service().get(USER, running.getId());

        assertThat(detail.circuitLoops()).isNull();
        assertThat(detail.circuitRestSec()).isNull();
        assertThat(detail.blocks()).extracting(WorkoutBlockResponse::groupKey)
                .containsExactly("A", "A", null);
    }

    @Test
    void swapBlock_backToPrescribed_prunesTheOverride() {
        GymTemplateVariant variant = variant();
        Exercise squat = squat();
        Exercise press = exercise("Presse");
        TemplateExercise te = prescription(variant, squat);
        WorkoutLog running = inProgress(variant, null);
        WorkoutBlockOverride override = new WorkoutBlockOverride(running, te);
        override.replaceWith(press);
        ReflectionTestUtils.setField(override, "id", UUID.randomUUID());

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(exerciseService.getOwned(USER, squat.getId())).thenReturn(squat);
        when(templateService.getAlternatives(te.getId()))
                .thenReturn(List.of(new TemplateExerciseAlternative(te, press, 0)));
        when(overrideRepository.findByWorkoutLogIdAndTemplateExerciseId(running.getId(), te.getId()))
                .thenReturn(Optional.of(override));
        when(setLogRepository.findLastWorkoutSets(USER, squat.getId())).thenReturn(List.of());
        when(setLogRepository.findRecordWeight(USER, squat.getId(), 6)).thenReturn(Optional.empty());

        WorkoutBlockResponse block = service().swapBlock(USER, running.getId(), te.getId(),
                new SwapBlockRequest(squat.getId()));

        assertThat(block.exercise().name()).isEqualTo("Squat");
        assertThat(block.swappedFrom()).isNull();
        verify(overrideRepository).delete(override);
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void skipThenRestore_prunesTheOverride() {
        GymTemplateVariant variant = variant();
        TemplateExercise te = prescription(variant, squat());
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(templateService.getAlternatives(te.getId())).thenReturn(List.of());
        when(setLogRepository.findLastWorkoutSets(USER, te.getExercise().getId()))
                .thenReturn(List.of());
        when(setLogRepository.findRecordWeight(USER, te.getExercise().getId(), 6))
                .thenReturn(Optional.empty());
        when(overrideRepository.findByWorkoutLogIdAndTemplateExerciseId(running.getId(), te.getId()))
                .thenReturn(Optional.empty());
        when(overrideRepository.save(any(WorkoutBlockOverride.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        WorkoutBlockResponse skipped = service().skipBlock(USER, running.getId(), te.getId());
        assertThat(skipped.skipped()).isTrue();

        WorkoutBlockOverride persisted = new WorkoutBlockOverride(running, te);
        persisted.skip();
        ReflectionTestUtils.setField(persisted, "id", UUID.randomUUID());
        when(overrideRepository.findByWorkoutLogIdAndTemplateExerciseId(running.getId(), te.getId()))
                .thenReturn(Optional.of(persisted));

        WorkoutBlockResponse restored = service().restoreBlock(USER, running.getId(), te.getId());
        assertThat(restored.skipped()).isFalse();
        verify(overrideRepository).delete(persisted);
    }

    @Test
    void deviations_rejectBlocksOfAnotherVariant() {
        WorkoutLog running = inProgress(variant(), null);
        TemplateExercise foreign = prescription(variant(), squat());

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().skipBlock(USER, running.getId(), foreign.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void detail_appliesOverrides() {
        GymTemplateVariant variant = variant();
        Exercise squat = squat();
        Exercise press = exercise("Presse");
        TemplateExercise te = prescription(variant, squat);
        WorkoutLog running = inProgress(variant, null);
        WorkoutBlockOverride override = new WorkoutBlockOverride(running, te);
        override.replaceWith(press);
        override.skip();

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(overrideRepository.findByWorkoutLogId(running.getId())).thenReturn(List.of(override));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(te));
        when(templateService.getAlternatives(te.getId())).thenReturn(List.of());
        when(setLogRepository.findLastWorkoutSets(USER, press.getId())).thenReturn(List.of());
        when(setLogRepository.findRecordWeight(USER, press.getId(), 6)).thenReturn(Optional.empty());
        when(setLogRepository.findByWorkoutLogIdOrderByPositionAscSetNumberAsc(running.getId()))
                .thenReturn(List.of());

        WorkoutDetailResponse detail = service().get(USER, running.getId());

        var block = detail.blocks().getFirst();
        assertThat(block.exercise().name()).isEqualTo("Presse");
        assertThat(block.swappedFrom().name()).isEqualTo("Squat");
        assertThat(block.skipped()).isTrue();
    }

    /* ── Mid-workout additions ────────────────────────────────────────── */

    @Test
    void addExtraBlock_appendsAnExerciseForThisWorkoutOnly() {
        GymTemplateVariant variant = variant();
        TemplateExercise te = prescription(variant, squat());
        Exercise calves = exercise("Mollets debout");
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(exerciseService.getOwned(USER, calves.getId())).thenReturn(calves);
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(te));
        when(extraBlockRepository.countByWorkoutLogId(running.getId())).thenReturn(0L);
        when(extraBlockRepository.save(any(WorkoutExtraBlock.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(setLogRepository.findLastWorkoutSets(USER, calves.getId())).thenReturn(List.of());
        when(setLogRepository.findRecordWeight(USER, calves.getId(), 12)).thenReturn(Optional.empty());

        WorkoutBlockResponse block = service().addExtraBlock(USER, running.getId(),
                new AddExtraBlockRequest(calves.getId(), 3, 12, null, 90, "unilatéral"));

        assertThat(block.templateExerciseId()).isNull();
        assertThat(block.exercise().name()).isEqualTo("Mollets debout");
        assertThat(block.sets()).isEqualTo(3);
        assertThat(block.targetReps()).isEqualTo(12);
        assertThat(block.restSec()).isEqualTo(90);
        assertThat(block.note()).isEqualTo("unilatéral");
    }

    @Test
    void addExtraBlock_rejectsAnExerciseAlreadyProgrammed() {
        GymTemplateVariant variant = variant();
        Exercise squat = squat();
        TemplateExercise te = prescription(variant, squat);
        WorkoutLog running = inProgress(variant, null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(exerciseService.getOwned(USER, squat.getId())).thenReturn(squat);
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(te));

        assertThatThrownBy(() -> service().addExtraBlock(USER, running.getId(),
                new AddExtraBlockRequest(squat.getId(), 3, 8, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà partie");
        verify(extraBlockRepository, never()).save(any());
    }

    @Test
    void removeExtraBlock_discardsItsLoggedSets() {
        GymTemplateVariant variant = variant();
        Exercise calves = exercise("Mollets debout");
        WorkoutLog running = inProgress(variant, null);
        WorkoutExtraBlock extra = new WorkoutExtraBlock(running, calves, 0, 3, 12, null, 90, null);
        ReflectionTestUtils.setField(extra, "id", UUID.randomUUID());
        SetLog logged = new SetLog(running, calves, 0, 1, 12, new BigDecimal("40.0"), null);

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(extraBlockRepository.findById(extra.getId())).thenReturn(Optional.of(extra));
        when(setLogRepository.findByWorkoutLogIdAndExerciseId(running.getId(), calves.getId()))
                .thenReturn(List.of(logged));

        service().removeExtraBlock(USER, running.getId(), extra.getId());

        verify(setLogRepository).deleteAll(List.of(logged));
        verify(extraBlockRepository).delete(extra);
    }

    @Test
    void detail_appendsExtraBlocksAfterTheProgram() {
        GymTemplateVariant variant = variant();
        TemplateExercise te = prescription(variant, squat());
        Exercise calves = exercise("Mollets debout");
        WorkoutLog running = inProgress(variant, null);
        WorkoutExtraBlock extra = new WorkoutExtraBlock(running, calves, 0, 3, 12, null, 90, null);
        ReflectionTestUtils.setField(extra, "id", UUID.randomUUID());

        when(workoutLogRepository.findById(running.getId())).thenReturn(Optional.of(running));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(variant.getId()))
                .thenReturn(List.of(te));
        when(extraBlockRepository.findByWorkoutLogIdOrderByPositionAsc(running.getId()))
                .thenReturn(List.of(extra));
        when(templateService.getAlternatives(te.getId())).thenReturn(List.of());
        when(setLogRepository.findLastWorkoutSets(any(), any())).thenReturn(List.of());
        when(setLogRepository.findRecordWeight(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
        when(setLogRepository.findByWorkoutLogIdOrderByPositionAscSetNumberAsc(running.getId()))
                .thenReturn(List.of());

        WorkoutDetailResponse detail = service().get(USER, running.getId());

        assertThat(detail.blocks()).hasSize(2);
        assertThat(detail.blocks().getFirst().templateExerciseId()).isEqualTo(te.getId());
        assertThat(detail.blocks().getLast().extraBlockId()).isEqualTo(extra.getId());
        assertThat(detail.blocks().getLast().exercise().name()).isEqualTo("Mollets debout");
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
