package com.cavale.gym.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.gym.domain.Equipment;
import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.ExerciseCategory;
import com.cavale.gym.domain.ExerciseMeasure;
import com.cavale.gym.domain.Muscle;
import com.cavale.gym.dto.ExerciseRequest;
import com.cavale.gym.repository.ExerciseRepository;
import com.cavale.gym.repository.TemplateExerciseAlternativeRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private TemplateExerciseRepository templateExerciseRepository;

    @Mock
    private TemplateExerciseAlternativeRepository alternativeRepository;

    private ExerciseService service() {
        return new ExerciseService(exerciseRepository, templateExerciseRepository,
                alternativeRepository);
    }

    private static ExerciseRequest squatRequest(String name) {
        return new ExerciseRequest(name, ExerciseCategory.FORCE, Equipment.BARBELL,
                ExerciseMeasure.WEIGHT_REPS, "Descendre sous la parallèle…",
                "https://youtube.com/watch?v=squat", "Force des quadris pour les descentes",
                Set.of(Muscle.QUADRICEPS, Muscle.FESSIERS), null, null);
    }

    private static Exercise squat() {
        Exercise exercise = new Exercise(USER, "Squat", ExerciseCategory.FORCE,
                Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS);
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        return exercise;
    }

    @Test
    void create_savesTheoryMusclesAndDerivation() {
        Exercise parent = squat();
        when(exerciseRepository.existsByUserIdAndNameIgnoreCase(USER, "Squat excentrique"))
                .thenReturn(false);
        when(exerciseRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciseRequest request = new ExerciseRequest("Squat excentrique", ExerciseCategory.FORCE,
                Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS, "Descente en 5 secondes", null,
                "Excentrique = casse musculaire des descentes longues",
                Set.of(Muscle.QUADRICEPS), parent.getId(), null);

        Exercise created = service().create(USER, request);

        assertThat(created.getName()).isEqualTo("Squat excentrique");
        assertThat(created.getDerivedFrom()).isSameAs(parent);
        assertThat(created.getMuscles()).containsExactly(Muscle.QUADRICEPS);
        assertThat(created.getDescription()).isEqualTo("Descente en 5 secondes");
    }

    @Test
    void create_rejectsDuplicateNamesCaseInsensitive() {
        when(exerciseRepository.existsByUserIdAndNameIgnoreCase(USER, "squat")).thenReturn(true);

        assertThatThrownBy(() -> service().create(USER, squatRequest("  squat ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe déjà");
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void update_allowsKeepingOwnName() {
        Exercise exercise = squat();
        when(exerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));

        service().update(USER, exercise.getId(), squatRequest("SQUAT"));

        assertThat(exercise.getName()).isEqualTo("SQUAT");
        verify(exerciseRepository, never()).existsByUserIdAndNameIgnoreCase(any(), any());
    }

    @Test
    void update_archivesWhenAsked() {
        Exercise exercise = squat();
        when(exerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));

        ExerciseRequest request = new ExerciseRequest("Squat", ExerciseCategory.FORCE,
                Equipment.BARBELL, ExerciseMeasure.WEIGHT_REPS, null, null, null, null, null, true);
        service().update(USER, exercise.getId(), request);

        assertThat(exercise.isArchived()).isTrue();
    }

    @Test
    void delete_refusesWhenReferencedByATemplate() {
        Exercise exercise = squat();
        when(exerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));
        when(templateExerciseRepository.existsByExerciseId(exercise.getId())).thenReturn(true);

        assertThatThrownBy(() -> service().delete(USER, exercise.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archive");
        verify(exerciseRepository, never()).delete(any(Exercise.class));
    }

    @Test
    void delete_refusesWhenChildrenDeriveFromIt() {
        Exercise exercise = squat();
        when(exerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));
        when(templateExerciseRepository.existsByExerciseId(exercise.getId())).thenReturn(false);
        when(alternativeRepository.existsByExerciseId(exercise.getId())).thenReturn(false);
        when(exerciseRepository.existsByDerivedFromId(exercise.getId())).thenReturn(true);

        assertThatThrownBy(() -> service().delete(USER, exercise.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dérivent");
    }

    @Test
    void delete_removesUnreferencedExercise() {
        Exercise exercise = squat();
        when(exerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));
        when(templateExerciseRepository.existsByExerciseId(exercise.getId())).thenReturn(false);
        when(alternativeRepository.existsByExerciseId(exercise.getId())).thenReturn(false);
        when(exerciseRepository.existsByDerivedFromId(exercise.getId())).thenReturn(false);

        service().delete(USER, exercise.getId());

        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);
        verify(exerciseRepository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(exercise);
    }

    @Test
    void foreignExercise_surfacesAsNotFound() {
        Exercise foreign = squat();
        ReflectionTestUtils.setField(foreign, "userId", UUID.randomUUID());
        when(exerciseRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().getOwned(USER, foreign.getId()))
                .isInstanceOf(com.cavale.common.exception.ResourceNotFoundException.class);
    }
}
