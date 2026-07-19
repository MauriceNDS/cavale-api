package com.cavale.training.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreateObjectiveRequest;
import com.cavale.training.dto.UpdateObjectiveRequest;
import com.cavale.training.repository.ObjectiveRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectiveServiceTest {

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private TrainingPlanService planService;

    private ObjectiveService service() {
        return new ObjectiveService(objectiveRepository, planService);
    }

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private static TrainingPlan plan() {
        TrainingPlan plan = new TrainingPlan(OWNER, "SaintéLyon 2026", "SaintéLyon 80 km",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        return plan;
    }

    private static Objective objective(TrainingPlan plan, ObjectiveRole role, String name, LocalDate date) {
        Objective objective = new Objective(plan, role, ObjectiveType.RACE, name, date);
        ReflectionTestUtils.setField(objective, "id", UUID.randomUUID());
        return objective;
    }

    @Test
    void addSecondary_savesSecondaryWithRaceProfile() {
        TrainingPlan plan = plan();
        when(planService.getOwnedPlan(OWNER, plan.getId())).thenReturn(plan);
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(inv -> inv.getArgument(0));

        Objective saved = service().addSecondary(OWNER, plan.getId(), new CreateObjectiveRequest(
                ObjectiveType.RACE, ObjectiveKind.TRAIL, ObjectiveIntensity.PERFORMANCE,
                "  Trail des Coursières 26 km ", LocalDate.of(2026, 9, 20),
                new BigDecimal("26.00"), 1100, 190, " Yzeron ", null));

        assertThat(saved.getRole()).isEqualTo(ObjectiveRole.SECONDARY);
        assertThat(saved.getName()).isEqualTo("Trail des Coursières 26 km");
        assertThat(saved.getKind()).isEqualTo(ObjectiveKind.TRAIL);
        assertThat(saved.getIntensity()).isEqualTo(ObjectiveIntensity.PERFORMANCE);
        assertThat(saved.getDistanceKm()).isEqualByComparingTo("26.00");
        assertThat(saved.getElevationGainM()).isEqualTo(1100);
        assertThat(saved.getTargetTimeMin()).isEqualTo(190);
        assertThat(saved.getLocation()).isEqualTo("Yzeron");
        assertThat(saved.getUserId()).isEqualTo(OWNER);
    }

    @Test
    void addSecondary_defaultsKindTrailAndIntensityBalanceWhenOmitted() {
        TrainingPlan plan = plan();
        when(planService.getOwnedPlan(OWNER, plan.getId())).thenReturn(plan);
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(inv -> inv.getArgument(0));

        Objective saved = service().addSecondary(OWNER, plan.getId(), new CreateObjectiveRequest(
                ObjectiveType.RACE, null, null, "10 km de Lyon", LocalDate.of(2026, 5, 1),
                new BigDecimal("10.00"), null, 42, null, null));

        assertThat(saved.getKind()).isEqualTo(ObjectiveKind.TRAIL);
        assertThat(saved.getIntensity()).isEqualTo(ObjectiveIntensity.BALANCE);
    }

    @Test
    void update_replacesEditableFieldsAndClearsOmittedOnes() {
        Objective objective = objective(plan(), ObjectiveRole.MAIN, "SaintéLyon 80 km", LocalDate.of(2026, 11, 29));
        objective.updateTargetTimeMin(720);
        when(objectiveRepository.findById(objective.getId())).thenReturn(Optional.of(objective));

        service().update(OWNER, objective.getId(), new UpdateObjectiveRequest(
                ObjectiveType.RACE, ObjectiveKind.TRAIL, ObjectiveIntensity.PERFORMANCE,
                "SaintéLyon 80 km 2026", LocalDate.of(2026, 11, 28),
                new BigDecimal("78.00"), 2100, null, null, "Saint-Étienne → Lyon", "Objectif finisher"));

        assertThat(objective.getName()).isEqualTo("SaintéLyon 80 km 2026");
        assertThat(objective.getDate()).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(objective.getDistanceKm()).isEqualByComparingTo("78.00");
        assertThat(objective.getTargetTimeMin()).isNull();
        assertThat(objective.getIntensity()).isEqualTo(ObjectiveIntensity.PERFORMANCE);
        assertThat(objective.getNotes()).isEqualTo("Objectif finisher");
    }

    @Test
    void update_keepsKindAndIntensityWhenOmitted() {
        Objective objective = objective(plan(), ObjectiveRole.MAIN, "SaintéLyon 80 km", LocalDate.of(2026, 11, 29));
        objective.updateKind(ObjectiveKind.ROAD);
        objective.updateIntensity(ObjectiveIntensity.PERFORMANCE);
        when(objectiveRepository.findById(objective.getId())).thenReturn(Optional.of(objective));

        service().update(OWNER, objective.getId(), new UpdateObjectiveRequest(
                ObjectiveType.RACE, null, null, "Marathon de Lyon", null,
                new BigDecimal("42.20"), null, 210, null, null, null));

        assertThat(objective.getKind()).isEqualTo(ObjectiveKind.ROAD);
        assertThat(objective.getIntensity()).isEqualTo(ObjectiveIntensity.PERFORMANCE);
    }

    @Test
    void update_hidesForeignObjectiveAs404() {
        Objective objective = objective(plan(), ObjectiveRole.SECONDARY, "Course B", null);
        when(objectiveRepository.findById(objective.getId())).thenReturn(Optional.of(objective));

        assertThatThrownBy(() -> service().update(STRANGER, objective.getId(), new UpdateObjectiveRequest(
                ObjectiveType.RACE, null, null, "X", null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesSecondaryObjective() {
        Objective objective = objective(plan(), ObjectiveRole.SECONDARY, "Course B", null);
        when(objectiveRepository.findById(objective.getId())).thenReturn(Optional.of(objective));

        service().delete(OWNER, objective.getId());

        verify(objectiveRepository).delete(objective);
    }

    @Test
    void delete_refusesMainObjective() {
        Objective objective = objective(plan(), ObjectiveRole.MAIN, "SaintéLyon 80 km", LocalDate.of(2026, 11, 29));
        when(objectiveRepository.findById(objective.getId())).thenReturn(Optional.of(objective));

        assertThatThrownBy(() -> service().delete(OWNER, objective.getId()))
                .isInstanceOf(com.cavale.common.exception.ConflictException.class);

        verify(objectiveRepository, never()).delete(any());
    }

    @Test
    void listForPlan_putsMainFirstThenSecondariesByDate() {
        TrainingPlan plan = plan();
        when(planService.getOwnedPlan(OWNER, plan.getId())).thenReturn(plan);
        Objective undated = objective(plan, ObjectiveRole.SECONDARY, "Sans date", null);
        Objective october = objective(plan, ObjectiveRole.SECONDARY, "Course octobre", LocalDate.of(2026, 10, 11));
        Objective main = objective(plan, ObjectiveRole.MAIN, "SaintéLyon", LocalDate.of(2026, 11, 29));
        Objective september = objective(plan, ObjectiveRole.SECONDARY, "Course septembre", LocalDate.of(2026, 9, 20));
        when(objectiveRepository.findByPlanId(plan.getId()))
                .thenReturn(List.of(undated, october, main, september));

        List<Objective> sorted = service().listForPlan(OWNER, plan.getId());

        assertThat(sorted).extracting(Objective::getName)
                .containsExactly("SaintéLyon", "Course septembre", "Course octobre", "Sans date");
    }
}
