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
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Shoe;
import com.cavale.training.domain.ShoePurpose;
import com.cavale.training.dto.ShoeRequest;
import com.cavale.training.dto.ShoeResponse;
import com.cavale.training.dto.ShoeStatsResponse;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ShoeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ShoeRepository shoeRepository;

    @Mock
    private ActivityRepository activityRepository;

    private ShoeService service() {
        return new ShoeService(shoeRepository, activityRepository);
    }

    @Test
    void create_savesShoeWithZeroMileage() {
        when(shoeRepository.save(any(Shoe.class))).thenAnswer(inv -> inv.getArgument(0));

        ShoeResponse response = service().create(USER,
                new ShoeRequest("Speedgoat 5", " Hoka ", "#14B4C8", "#F97316", ShoePurpose.TRAIL, 800, null, null));

        assertThat(response.name()).isEqualTo("Speedgoat 5");
        assertThat(response.brand()).isEqualTo("Hoka");
        assertThat(response.color()).isEqualTo("#14B4C8");
        assertThat(response.colorSecondary()).isEqualTo("#F97316");
        assertThat(response.purpose()).isEqualTo(ShoePurpose.TRAIL);
        assertThat(response.isDefault()).isFalse();
        assertThat(response.mileageKm()).isEqualByComparingTo("0.0");
        assertThat(response.needsRetirement()).isFalse();
    }

    @Test
    void create_asDefault_clearsTheOtherDefault() {
        Shoe existing = new Shoe(USER, "Old pair");
        existing.update("Old pair", "Nike", "#111111", null, ShoePurpose.ROAD, null, false);
        existing.markDefault();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(shoeRepository.findByUserIdOrderByRetiredAscCreatedAtDesc(USER)).thenReturn(List.of(existing));
        when(shoeRepository.save(any(Shoe.class))).thenAnswer(inv -> inv.getArgument(0));

        ShoeResponse response = service().create(USER,
                new ShoeRequest("New pair", "Hoka", "#14B4C8", null, ShoePurpose.TRAIL, 800, null, true));

        assertThat(response.isDefault()).isTrue();
        assertThat(existing.isDefault()).isFalse(); // the previous default was cleared
    }

    @Test
    void list_sumsMileageAndFlagsRetirement() {
        Shoe shoe = new Shoe(USER, "Speedgoat 5");
        shoe.update("Speedgoat 5", "Hoka", "#14B4C8", null, ShoePurpose.TRAIL, 800, false);
        UUID shoeId = UUID.randomUUID();
        ReflectionTestUtils.setField(shoe, "id", shoeId);
        when(shoeRepository.findByUserIdOrderByRetiredAscCreatedAtDesc(USER)).thenReturn(List.of(shoe));

        ActivityRepository.ShoeMileage mileage = mock(ActivityRepository.ShoeMileage.class);
        when(mileage.getShoeId()).thenReturn(shoeId);
        when(mileage.getTotalKm()).thenReturn(new BigDecimal("824.6"));
        when(activityRepository.mileageByShoe(USER)).thenReturn(List.of(mileage));

        ShoeResponse response = service().list(USER).getFirst();

        assertThat(response.mileageKm()).isEqualByComparingTo("824.6");
        assertThat(response.needsRetirement()).isTrue(); // 824.6 >= 800, still active
    }

    @Test
    void requireOwned_passesNullAndRejectsAForeignShoe() {
        assertThat(service().requireOwned(USER, null)).isNull();

        Shoe foreign = new Shoe(UUID.randomUUID(), "Not yours");
        UUID shoeId = UUID.randomUUID();
        ReflectionTestUtils.setField(foreign, "id", shoeId);
        when(shoeRepository.findById(shoeId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().requireOwned(USER, shoeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Shoe ownedShoe() {
        Shoe shoe = new Shoe(USER, "Speedgoat 5");
        ReflectionTestUtils.setField(shoe, "id", UUID.randomUUID());
        return shoe;
    }

    private static Activity run(LocalDate date, String km, Integer elevation, int durationMin) {
        return Activity.manual(USER, date, durationMin, new BigDecimal(km), elevation, null, "run");
    }

    @Test
    void stats_aggregateRunsAndMonthlyMileage() {
        Shoe shoe = ownedShoe();
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        when(shoeRepository.findById(shoe.getId())).thenReturn(Optional.of(shoe));
        when(activityRepository.findByUserIdAndShoeIdOrderByDateAsc(USER, shoe.getId()))
                .thenReturn(List.of(
                        run(thisMonth.minusMonths(1), "10.0", 200, 60),
                        run(thisMonth, "12.0", 100, 72),
                        run(thisMonth.plusDays(1), "8.0", null, 48)));

        ShoeStatsResponse stats = service().stats(USER, shoe.getId());

        assertThat(stats.runs()).isEqualTo(3);
        assertThat(stats.totalKm()).isEqualByComparingTo("30.0");
        assertThat(stats.totalElevationM()).isEqualTo(300);
        assertThat(stats.firstUsedOn()).isEqualTo(thisMonth.minusMonths(1));
        assertThat(stats.lastUsedOn()).isEqualTo(thisMonth.plusDays(1));
        // 180 minutes over 30 km = 6:00/km
        assertThat(stats.avgPaceSecPerKm()).isEqualTo(360);
        assertThat(stats.monthlyKm()).hasSize(6);
        assertThat(stats.monthlyKm().getLast().km()).isEqualByComparingTo("20.0");
        assertThat(stats.monthlyKm().get(4).km()).isEqualByComparingTo("10.0");
    }

    @Test
    void stats_ofAnEmptyPair_areAllZero() {
        Shoe shoe = ownedShoe();
        when(shoeRepository.findById(shoe.getId())).thenReturn(Optional.of(shoe));
        when(activityRepository.findByUserIdAndShoeIdOrderByDateAsc(USER, shoe.getId()))
                .thenReturn(List.of());

        ShoeStatsResponse stats = service().stats(USER, shoe.getId());

        assertThat(stats.runs()).isZero();
        assertThat(stats.totalKm()).isEqualByComparingTo("0.0");
        assertThat(stats.firstUsedOn()).isNull();
        assertThat(stats.avgPaceSecPerKm()).isNull();
    }

    @Test
    void assignToActivity_setsAndClearsTheShoe() {
        Shoe shoe = ownedShoe();
        Activity activity = run(LocalDate.now(), "10.0", null, 60);
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(shoeRepository.findById(shoe.getId())).thenReturn(Optional.of(shoe));

        assertThat(service().assignToActivity(USER, activity.getId(), shoe.getId()))
                .isEqualTo(shoe.getId());
        assertThat(activity.getShoeId()).isEqualTo(shoe.getId());

        assertThat(service().assignToActivity(USER, activity.getId(), null)).isNull();
        assertThat(activity.getShoeId()).isNull();
    }

    @Test
    void assignToActivity_rejectsAForeignShoe() {
        Activity activity = run(LocalDate.now(), "10.0", null, 60);
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        Shoe foreign = new Shoe(UUID.randomUUID(), "Pas à moi");
        ReflectionTestUtils.setField(foreign, "id", UUID.randomUUID());
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(shoeRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().assignToActivity(USER, activity.getId(), foreign.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(activity.getShoeId()).isNull();
    }
}
