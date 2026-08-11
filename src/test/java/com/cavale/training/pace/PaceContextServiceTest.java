package com.cavale.training.pace;

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

import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveIntensity;
import com.cavale.training.domain.ObjectiveKind;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.PaceContextResponse;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.training.workout.WorkoutStructure.Allure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaceContextServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 26);

    @Mock
    private PaceModelService paceModelService;

    @Mock
    private TrainingPlanRepository planRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private com.cavale.user.service.UserService userService;

    @Mock
    private com.cavale.training.repository.ActivityRepository activityRepository;

    private PaceContextService service() {
        com.cavale.user.domain.User user = new com.cavale.user.domain.User("a@b.c", "x", "Ops");
        org.mockito.Mockito.lenient().when(userService.getById(USER)).thenReturn(user);
        org.mockito.Mockito.lenient().when(activityRepository.findObservedMaxHr(
                org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
                .thenReturn(190);
        return new PaceContextService(paceModelService, planRepository, objectiveRepository,
                userService, activityRepository);
    }

    private static TrainingPlan activePlan() {
        TrainingPlan plan = new TrainingPlan(USER, "Saison", null,
                TODAY.minusWeeks(4), TODAY.plusWeeks(8));
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        return plan; // constructor makes it ACTIVE
    }

    private static Objective main(TrainingPlan plan, ObjectiveKind kind,
                                  BigDecimal distanceKm, Integer targetTimeMin) {
        Objective main = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                "Objectif", plan.getEndDate());
        main.updateKind(kind);
        main.updateIntensity(ObjectiveIntensity.BALANCE);
        if (distanceKm != null) {
            main.updateRaceProfile(distanceKm, null, null);
        }
        if (targetTimeMin != null) {
            main.updateTargetTimeMin(targetTimeMin);
        }
        return main;
    }

    @Test
    void roadRaceWithTarget_anchorsGoalPace() {
        TrainingPlan plan = activePlan();
        when(paceModelService.modelFor(USER)).thenReturn(PaceModel.fallback());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of(plan));
        when(objectiveRepository.findByPlanIdAndRole(plan.getId(), ObjectiveRole.MAIN))
                .thenReturn(Optional.of(main(plan, ObjectiveKind.ROAD, new BigDecimal("42.20"), 240)));

        PaceContextResponse context = service().contextFor(USER, TODAY);

        assertThat(context.roadContext()).isTrue();
        assertThat(context.goalPaceSecPerKm()).isEqualTo(341); // 4h over 42.2 km ≈ 5:41/km
        assertThat(context.flatSecPerKm()).containsEntry(Allure.EF, 390);
    }

    @Test
    void trailSeason_disablesPaceBands() {
        TrainingPlan plan = activePlan();
        when(paceModelService.modelFor(USER)).thenReturn(PaceModel.fallback());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of(plan));
        when(objectiveRepository.findByPlanIdAndRole(plan.getId(), ObjectiveRole.MAIN))
                .thenReturn(Optional.of(main(plan, ObjectiveKind.TRAIL, null, null)));

        PaceContextResponse context = service().contextFor(USER, TODAY);

        assertThat(context.roadContext()).isFalse();
        assertThat(context.goalPaceSecPerKm()).isNull();
    }

    @Test
    void noSeason_stillReturnsTheModel() {
        when(paceModelService.modelFor(USER)).thenReturn(PaceModel.fallback());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of());

        PaceContextResponse context = service().contextFor(USER, TODAY);

        assertThat(context.roadContext()).isFalse();
        assertThat(context.personal()).isFalse();
        assertThat(context.flatSecPerKm()).isNotEmpty();
    }

    @Test
    void roadWithoutTargetTime_hasNoGoalPace() {
        TrainingPlan plan = activePlan();
        when(paceModelService.modelFor(USER)).thenReturn(PaceModel.fallback());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of(plan));
        when(objectiveRepository.findByPlanIdAndRole(plan.getId(), ObjectiveRole.MAIN))
                .thenReturn(Optional.of(main(plan, ObjectiveKind.ROAD, new BigDecimal("21.10"), null)));

        PaceContextResponse context = service().contextFor(USER, TODAY);

        assertThat(context.roadContext()).isTrue();
        assertThat(context.goalPaceSecPerKm()).isNull();
    }
}
