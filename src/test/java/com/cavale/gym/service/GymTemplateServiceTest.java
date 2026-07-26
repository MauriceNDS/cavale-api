package com.cavale.gym.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
import com.cavale.gym.domain.GymTemplate;
import com.cavale.gym.domain.GymTemplateVariant;
import com.cavale.gym.domain.TemplateExercise;
import com.cavale.gym.domain.TemplateExerciseAlternative;
import com.cavale.gym.dto.TemplateDtos.GroupAssignment;
import com.cavale.gym.dto.TemplateDtos.ReorderRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseRequest;
import com.cavale.gym.dto.TemplateDtos.TemplateExerciseResponse;
import com.cavale.gym.dto.TemplateDtos.TemplateRequest;
import com.cavale.gym.dto.TemplateDtos.VariantRequest;
import com.cavale.gym.repository.GymTemplateRepository;
import com.cavale.gym.repository.GymTemplateVariantRepository;
import com.cavale.gym.repository.TemplateExerciseAlternativeRepository;
import com.cavale.gym.repository.TemplateExerciseRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymTemplateServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private GymTemplateRepository templateRepository;

    @Mock
    private GymTemplateVariantRepository variantRepository;

    @Mock
    private TemplateExerciseRepository templateExerciseRepository;

    @Mock
    private TemplateExerciseAlternativeRepository alternativeRepository;

    @Mock
    private ExerciseService exerciseService;

    private GymTemplateService service() {
        return new GymTemplateService(templateRepository, variantRepository,
                templateExerciseRepository, alternativeRepository, exerciseService);
    }

    private static GymTemplate template() {
        GymTemplate template = new GymTemplate(USER, "Force Max", "Cycle force hivernal");
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        return template;
    }

    private static GymTemplateVariant variant(GymTemplate template, String label) {
        GymTemplateVariant variant = new GymTemplateVariant(template, label, null);
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        return variant;
    }

    private static Exercise exercise(String name, ExerciseMeasure measure) {
        Exercise exercise = new Exercise(USER, name, ExerciseCategory.FORCE,
                Equipment.BARBELL, measure);
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        return exercise;
    }

    private static TemplateExercise prescription(GymTemplateVariant variant, Exercise exercise,
                                                 int position) {
        TemplateExercise te = new TemplateExercise(variant, exercise, position, 3, 6, null,
                180, 75, null);
        ReflectionTestUtils.setField(te, "id", UUID.randomUUID());
        return te;
    }

    @Test
    void createTemplate_isBornWithVariantA() {
        when(templateRepository.existsByUserIdAndNameIgnoreCase(USER, "Force Max")).thenReturn(false);
        when(templateRepository.save(any(GymTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variantRepository.save(any(GymTemplateVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        service().createTemplate(USER, new TemplateRequest("Force Max", "Cycle force", null));

        ArgumentCaptor<GymTemplateVariant> captor = ArgumentCaptor.forClass(GymTemplateVariant.class);
        verify(variantRepository).save(captor.capture());
        assertThat(captor.getValue().getLabel()).isEqualTo("A");
    }

    @Test
    void createTemplate_rejectsDuplicateName() {
        when(templateRepository.existsByUserIdAndNameIgnoreCase(USER, "Force Max")).thenReturn(true);

        assertThatThrownBy(() -> service()
                .createTemplate(USER, new TemplateRequest("Force Max", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void addVariant_rejectsDuplicateLabel() {
        GymTemplate template = template();
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(variantRepository.existsByTemplateIdAndLabelIgnoreCase(template.getId(), "a"))
                .thenReturn(true);

        assertThatThrownBy(() -> service()
                .addVariant(USER, template.getId(), new VariantRequest("a", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void copyVariant_clonesExercisesAndAlternatives() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        Exercise squat = exercise("Squat", ExerciseMeasure.WEIGHT_REPS);
        Exercise gobelet = exercise("Gobelet squat", ExerciseMeasure.WEIGHT_REPS);
        TemplateExercise te = prescription(a, squat, 0);
        TemplateExerciseAlternative alt = new TemplateExerciseAlternative(te, gobelet, 0);

        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(variantRepository.existsByTemplateIdAndLabelIgnoreCase(template.getId(), "B"))
                .thenReturn(false);
        when(variantRepository.save(any(GymTemplateVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(a.getId()))
                .thenReturn(List.of(te));
        when(templateExerciseRepository.save(any(TemplateExercise.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(alternativeRepository.findByTemplateExerciseIdOrderByPositionAsc(te.getId()))
                .thenReturn(List.of(alt));
        when(alternativeRepository.save(any(TemplateExerciseAlternative.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GymTemplateVariant copy = service().copyVariant(USER, a.getId(), new VariantRequest("B", null));

        assertThat(copy.getLabel()).isEqualTo("B");
        ArgumentCaptor<TemplateExercise> teCaptor = ArgumentCaptor.forClass(TemplateExercise.class);
        verify(templateExerciseRepository).save(teCaptor.capture());
        assertThat(teCaptor.getValue().getExercise()).isSameAs(squat);
        assertThat(teCaptor.getValue().getSets()).isEqualTo(3);
        ArgumentCaptor<TemplateExerciseAlternative> altCaptor =
                ArgumentCaptor.forClass(TemplateExerciseAlternative.class);
        verify(alternativeRepository).save(altCaptor.capture());
        assertThat(altCaptor.getValue().getExercise()).isSameAs(gobelet);
    }

    @Test
    void deleteVariant_keepsAtLeastOne() {
        GymTemplate template = template();
        GymTemplateVariant only = variant(template, "A");
        when(variantRepository.findById(only.getId())).thenReturn(Optional.of(only));
        when(variantRepository.countByTemplateId(template.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service().deleteVariant(USER, only.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au moins une variante");
    }

    @Test
    void addExercise_appendsAtTheEndAndChecksMeasure() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        Exercise squat = exercise("Squat", ExerciseMeasure.WEIGHT_REPS);
        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(exerciseService.getOwned(USER, squat.getId())).thenReturn(squat);
        when(templateExerciseRepository.countByVariantId(a.getId())).thenReturn(2L);
        when(templateExerciseRepository.save(any(TemplateExercise.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TemplateExercise te = service().addExercise(USER, a.getId(),
                new TemplateExerciseRequest(squat.getId(), 3, 6, null, 180, 75, null, null));

        assertThat(te.getPosition()).isEqualTo(2);
        assertThat(te.getReps()).isEqualTo(6);
    }

    @Test
    void addExercise_secondsExerciseNeedsSeconds() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        Exercise planche = exercise("Planche", ExerciseMeasure.SECONDS);
        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(exerciseService.getOwned(USER, planche.getId())).thenReturn(planche);

        assertThatThrownBy(() -> service().addExercise(USER, a.getId(),
                new TemplateExerciseRequest(planche.getId(), 3, 12, null, 60, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secondes");
        verify(templateExerciseRepository, never()).save(any());
    }

    @Test
    void reorder_requiresTheExactExerciseSet() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        TemplateExercise first = prescription(a, exercise("Squat", ExerciseMeasure.WEIGHT_REPS), 0);
        TemplateExercise second = prescription(a, exercise("Fentes", ExerciseMeasure.WEIGHT_REPS), 1);
        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(a.getId()))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service().reorderExercises(USER, a.getId(),
                new ReorderRequest(List.of(first.getId()))))
                .isInstanceOf(IllegalArgumentException.class);

        List<TemplateExerciseResponse> response = service().reorderExercises(USER, a.getId(),
                new ReorderRequest(List.of(second.getId(), first.getId())));
        assertThat(second.getPosition()).isZero();
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(response).extracting(r -> r.exercise().name())
                .containsExactly("Squat", "Fentes");
    }

    @Test
    void assignGroups_chainsConsecutivePrescriptions() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        TemplateExercise squat = prescription(a, exercise("Squat", ExerciseMeasure.WEIGHT_REPS), 0);
        TemplateExercise planche = prescription(a, exercise("Planche", ExerciseMeasure.WEIGHT_REPS), 1);
        TemplateExercise presse = prescription(a, exercise("Presse", ExerciseMeasure.WEIGHT_REPS), 2);
        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(a.getId()))
                .thenReturn(List.of(squat, planche, presse));

        service().assignGroups(USER, a.getId(), List.of(
                new GroupAssignment(squat.getId(), "A"),
                new GroupAssignment(planche.getId(), "A"),
                new GroupAssignment(presse.getId(), null)));

        assertThat(squat.getGroupKey()).isEqualTo("A");
        assertThat(planche.getGroupKey()).isEqualTo("A");
        assertThat(presse.getGroupKey()).isNull();
    }

    @Test
    void assignGroups_rejectsAGroupSplitAcrossTheList() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        TemplateExercise squat = prescription(a, exercise("Squat", ExerciseMeasure.WEIGHT_REPS), 0);
        TemplateExercise presse = prescription(a, exercise("Presse", ExerciseMeasure.WEIGHT_REPS), 1);
        TemplateExercise planche = prescription(a, exercise("Planche", ExerciseMeasure.WEIGHT_REPS), 2);
        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(a.getId()))
                .thenReturn(List.of(squat, presse, planche));

        // a superset is performed in rotation — its members cannot be strangers
        assertThatThrownBy(() -> service().assignGroups(USER, a.getId(), List.of(
                new GroupAssignment(squat.getId(), "A"),
                new GroupAssignment(presse.getId(), null),
                new GroupAssignment(planche.getId(), "A"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("se suivre");
    }

    @Test
    void assignGroups_dropsAKeyLeftWithASingleMember() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        TemplateExercise squat = prescription(a, exercise("Squat", ExerciseMeasure.WEIGHT_REPS), 0);
        TemplateExercise presse = prescription(a, exercise("Presse", ExerciseMeasure.WEIGHT_REPS), 1);
        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(a.getId()))
                .thenReturn(List.of(squat, presse));

        service().assignGroups(USER, a.getId(), List.of(
                new GroupAssignment(squat.getId(), "A"),
                new GroupAssignment(presse.getId(), null)));

        assertThat(squat.getGroupKey()).isNull(); // a superset of one is not a superset
    }

    @Test
    void reorder_splitsASupersetItsMembersLeft() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        TemplateExercise squat = prescription(a, exercise("Squat", ExerciseMeasure.WEIGHT_REPS), 0);
        TemplateExercise planche = prescription(a, exercise("Planche", ExerciseMeasure.WEIGHT_REPS), 1);
        TemplateExercise presse = prescription(a, exercise("Presse", ExerciseMeasure.WEIGHT_REPS), 2);
        squat.assignGroup("A");
        planche.assignGroup("A");
        when(variantRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(templateExerciseRepository.findByVariantIdOrderByPositionAsc(a.getId()))
                .thenAnswer(inv -> Stream.of(squat, planche, presse)
                        .sorted(Comparator.comparingInt(TemplateExercise::getPosition)).toList());

        // drag Presse between the two partners rather than failing the drop
        service().reorderExercises(USER, a.getId(),
                new ReorderRequest(List.of(squat.getId(), presse.getId(), planche.getId())));

        assertThat(squat.getGroupKey()).isNull();
        assertThat(planche.getGroupKey()).isNull();
    }

    @Test
    void addAlternative_rejectsSelfAndDuplicates() {
        GymTemplate template = template();
        GymTemplateVariant a = variant(template, "A");
        Exercise squat = exercise("Squat", ExerciseMeasure.WEIGHT_REPS);
        TemplateExercise te = prescription(a, squat, 0);
        when(templateExerciseRepository.findById(te.getId())).thenReturn(Optional.of(te));
        when(exerciseService.getOwned(USER, squat.getId())).thenReturn(squat);

        assertThatThrownBy(() -> service().addAlternative(USER, te.getId(), squat.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lui-même");

        Exercise gobelet = exercise("Gobelet squat", ExerciseMeasure.WEIGHT_REPS);
        when(exerciseService.getOwned(USER, gobelet.getId())).thenReturn(gobelet);
        when(alternativeRepository.existsByTemplateExerciseIdAndExerciseId(te.getId(), gobelet.getId()))
                .thenReturn(true);
        assertThatThrownBy(() -> service().addAlternative(USER, te.getId(), gobelet.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà proposée");
    }

    @Test
    void foreignTemplate_surfacesAsNotFound() {
        GymTemplate foreign = template();
        ReflectionTestUtils.setField(foreign, "userId", UUID.randomUUID());
        when(templateRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().getOwnedTemplate(USER, foreign.getId()))
                .isInstanceOf(com.cavale.common.exception.ResourceNotFoundException.class);
        verify(templateRepository, times(1)).findById(foreign.getId());
        verify(variantRepository, never()).findByTemplateIdOrderByLabelAsc(eq(foreign.getId()));
    }
}
