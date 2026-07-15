package com.cavale.training.course;

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

import com.cavale.athlete.service.RunningStatsService;
import com.cavale.athlete.service.RunningStatsService.TrailPace;
import com.cavale.training.domain.Course;
import com.cavale.training.domain.CourseWaypoint;
import com.cavale.training.domain.WaypointKind;
import com.cavale.training.dto.CourseResponse;
import com.cavale.training.repository.CourseRepository;
import com.cavale.training.repository.CourseWaypointRepository;
import com.cavale.training.repository.ObjectiveRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OBJECTIVE = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
    /** 20 km climbing 1000 m over the first half, then flat. */
    private static final String PROFILE = "[[0,0],[2000,200],[4000,400],[6000,600],[8000,800],"
            + "[10000,1000],[12000,1000],[14000,1000],[16000,1000],[18000,1000],[20000,1000]]";

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseWaypointRepository waypointRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private RunningStatsService runningStatsService;

    private CourseService service() {
        return new CourseService(courseRepository, waypointRepository, objectiveRepository,
                runningStatsService);
    }

    private Course course() {
        Course course = new Course(USER, OBJECTIVE, "6000D", new BigDecimal("20.00"), 1000, 200, PROFILE);
        ReflectionTestUtils.setField(course, "id", UUID.randomUUID());
        when(courseRepository.findByObjectiveId(OBJECTIVE)).thenReturn(Optional.of(course));
        return course;
    }

    @Test
    void getCourse_gradeAdjustsSplitsAndArrivalsFromTheTrailPace() {
        Course course = course();
        CourseWaypoint aid = new CourseWaypoint(course.getId(), "Ravito 10k",
                WaypointKind.AID_STATION, new BigDecimal("10.00"), 1000, null);
        ReflectionTestUtils.setField(aid, "id", UUID.randomUUID());
        when(waypointRepository.findByCourseIdOrderByDistanceKm(course.getId())).thenReturn(List.of(aid));
        // 300 s per km-effort; the course's km-effort (30) equals the base, so fatigue = 1
        when(runningStatsService.trailPace(USER, TODAY))
                .thenReturn(new TrailPace(280, 300, 330, 30, 1.07, 5));

        CourseResponse response = service().getCourse(USER, OBJECTIVE, TODAY);

        assertThat(response.kmEffort()).isEqualByComparingTo("30"); // 20 km + 1000/100
        assertThat(response.paceMedianSecPerKmEffort()).isEqualTo(300);
        assertThat(response.finishMidSec()).isEqualTo(9000); // 300 × 30
        assertThat(response.finishLowSec()).isLessThan(response.finishMidSec());
        assertThat(response.finishHighSec()).isGreaterThan(response.finishMidSec());

        CourseResponse.Waypoint arrival = response.waypoints().getFirst();
        assertThat(arrival.climbToM()).isEqualTo(1000);
        assertThat(arrival.midSec()).isEqualTo(6000); // 300 × (10 + 1000/100)

        assertThat(response.splits()).isNotEmpty();
        assertThat(response.splits().getFirst().cumulativeSec()).isEqualTo(1200); // 300 × (2 + 200/100)
    }

    @Test
    void getCourse_leavesTimesNullWithoutTrailHistory() {
        Course course = course();
        when(waypointRepository.findByCourseIdOrderByDistanceKm(course.getId())).thenReturn(List.of());
        when(runningStatsService.trailPace(USER, TODAY)).thenReturn(null);

        CourseResponse response = service().getCourse(USER, OBJECTIVE, TODAY);

        assertThat(response.kmEffort()).isEqualByComparingTo("30"); // profile still renders
        assertThat(response.finishMidSec()).isNull();
        assertThat(response.paceMedianSecPerKmEffort()).isNull();
        assertThat(response.splits().getFirst().cumulativeSec()).isNull();
    }
}
