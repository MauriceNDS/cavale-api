package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.athlete.dto.ActivityDetailResponse;
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.gym.repository.WorkoutLogRepository;
import com.cavale.training.domain.Activity;
import com.cavale.training.repository.ActivityRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityFeedServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private WorkoutLogRepository workoutLogRepository;
    @Mock
    private SetLogRepository setLogRepository;

    private ActivityFeedService service() {
        return new ActivityFeedService(activityRepository, workoutLogRepository, setLogRepository);
    }

    private static Activity ownedRun() {
        Activity activity = Activity.stravaHistory(OWNER, LocalDate.of(2026, 6, 1), 62,
                new BigDecimal("10.50"), 180, 149, "Morning run", 7L);
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        return activity;
    }

    @Test
    void activityDetail_returnsOwnActivity() {
        Activity activity = ownedRun();
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        ActivityDetailResponse detail = service().activityDetail(OWNER, activity.getId());

        assertThat(detail.name()).isEqualTo("Morning run");
        assertThat(detail.distanceKm()).isEqualByComparingTo("10.50");
        assertThat(detail.sessionId()).isNull();
        assertThat(detail.hasStreams()).isFalse();
    }

    @Test
    void activityDetail_hidesForeignActivityAs404() {
        Activity activity = ownedRun();
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> service().activityDetail(STRANGER, activity.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activityStreams_emptyWhenNone() {
        Activity activity = ownedRun();
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThat(service().activityStreams(OWNER, activity.getId())).isEmpty();
    }
}
