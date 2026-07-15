package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.athlete.dto.AthleteHubResponse;
import com.cavale.athlete.dto.AthleteHubResponse.DistanceRecord;
import com.cavale.athlete.dto.AthleteHubResponse.MonthlyStat;
import com.cavale.athlete.dto.AthleteHubResponse.Prediction;
import com.cavale.athlete.dto.AthleteHubResponse.Timeframe;
import com.cavale.athlete.dto.AthleteHubResponse.TrailIndex;
import com.cavale.athlete.dto.AthleteHubResponse.WeeklyEffort;
import com.cavale.integration.strava.StravaConnectionRepository;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.user.domain.User;
import com.cavale.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteStatsServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 9);

    @Mock
    private UserService userService;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityBestEffortRepository bestEffortRepository;

    @Mock
    private TrainingPlanRepository planRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private StravaConnectionRepository connectionRepository;

    private AthleteStatsService service() {
        return new AthleteStatsService(userService, activityRepository, bestEffortRepository,
                planRepository, objectiveRepository, connectionRepository);
    }

    private static Activity run(LocalDate date, int minutes, String km, Integer elevation,
                                Integer hr, Integer effort, String cadence) {
        Activity activity = Activity.stravaHistory(USER, date, minutes,
                km != null ? new BigDecimal(km) : null, elevation, hr, "Run " + date, date.toEpochDay());
        activity.enrich(cadence != null ? new BigDecimal(cadence) : null, effort, null);
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        return activity;
    }

    private static ActivityBestEffort effort(Activity activity, String name, int distanceM, int sec) {
        return new ActivityBestEffort(activity, name, distanceM, sec);
    }

    /* ── Records ───────────────────────────────────────────────────────── */

    @Test
    void records_pickFastestPerCanonicalDistanceWithTolerance() {
        Activity a1 = run(LocalDate.of(2025, 4, 10), 60, "12.00", 100, 150, 40, null);
        Activity a2 = run(LocalDate.of(2026, 3, 2), 50, "10.00", 80, 155, 35, null);
        List<ActivityBestEffort> efforts = List.of(
                effort(a1, "5k", 5000, 1500),
                effort(a2, "5k", 5010, 1420),      // within 1 % tolerance, faster
                effort(a1, "10k", 10000, 3100),
                effort(a2, "400m", 400, 80));       // not a canonical distance

        List<DistanceRecord> records = AthleteStatsService.records(efforts);

        assertThat(records).extracting(DistanceRecord::label).containsExactly("5 km", "10 km");
        DistanceRecord fiveK = records.getFirst();
        assertThat(fiveK.seconds()).isEqualTo(1420);
        assertThat(fiveK.date()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void roadRecords_excludeHillyEffortsSoTrailDoesNotDeflateRoadEstimates() {
        // a quick 10k split run on 700 m of D+, and a slower one on flat road
        Activity hilly = run(LocalDate.of(2026, 4, 5), 45, "10.00", 700, 150, 90, null); // 70 m/km → trail
        Activity flat = run(LocalDate.of(2026, 3, 2), 48, "10.00", 60, 150, 40, null);   // 6 m/km → road
        List<ActivityBestEffort> efforts = List.of(
                effort(hilly, "10k", 10000, 2700),
                effort(flat, "10k", 10000, 2880));

        // the records wall keeps the athlete's actual best — the hilly one
        assertThat(AthleteStatsService.records(efforts))
                .extracting(DistanceRecord::seconds).containsExactly(2700);
        // but the road predictors ignore the hilly split and fall back to the flat one
        assertThat(AthleteStatsService.roadRecords(efforts))
                .extracting(DistanceRecord::seconds).containsExactly(2880);
    }

    @Test
    void roadRecords_emptyWhenEverySplitIsTrail() {
        Activity hilly = run(LocalDate.of(2026, 4, 5), 60, "10.00", 900, 150, 90, null);
        List<ActivityBestEffort> efforts = List.of(effort(hilly, "10k", 10000, 3000));

        assertThat(AthleteStatsService.roadRecords(efforts)).isEmpty();
        assertThat(AthleteStatsService.records(efforts)).hasSize(1); // a record is still a record
    }

    /* ── Trail performance index (P7) ──────────────────────────────────── */

    @Test
    void trailIndex_rewardsBiggerFasterTrailEffortsRecencyWeighted() {
        List<Activity> activities = List.of(
                run(TODAY.minusMonths(2), 90, "15.00", 800, 145, 60, null),   // 23 km-effort
                run(TODAY.minusMonths(6), 240, "42.00", 2400, 150, 200, null), // 66 km-effort — best
                run(TODAY.minusMonths(20), 120, "20.00", 1000, 148, 90, null), // 30 km-effort
                run(TODAY.minusDays(5), 30, "6.00", 400, 140, 30, null));      // too short/short → skipped

        TrailIndex index = AthleteStatsService.trailIndex(activities, TODAY);

        assertThat(index).isNotNull();
        assertThat(index.sampleEfforts()).isEqualTo(3);
        assertThat(index.index()).isPositive();
        // the 42 km / 2400 m D+ run scores highest → it is the reference effort
        assertThat(index.bestKmEffort()).isEqualByComparingTo("66"); // 42 + 2400/100
        assertThat(index.bestEffortDate()).isEqualTo(TODAY.minusMonths(6));
    }

    @Test
    void trailIndex_nullWithoutEnoughTrailEfforts() {
        // a flat road run is not a trail effort — under the minimum
        assertThat(AthleteStatsService.trailIndex(List.of(
                run(TODAY.minusDays(3), 90, "15.00", 100, 145, 60, null)), TODAY)).isNull();
    }

    /* ── Predictions (Riegel) ──────────────────────────────────────────── */

    @Test
    void predictions_useClosestRecordAndRiegelFormula() {
        List<DistanceRecord> records = List.of(
                new DistanceRecord("10 km", 10000, 2700, LocalDate.of(2026, 3, 2), "Course test"));

        List<Prediction> predictions = AthleteStatsService.predictions(records);

        // no 10 km estimation: the athlete HAS a 10 km record — that's the time
        assertThat(predictions).extracting(Prediction::label)
                .containsExactly("5 km", "Semi", "Marathon");
        Prediction half = predictions.stream()
                .filter(p -> p.label().equals("Semi")).findFirst().orElseThrow();
        // 2700 × (21097/10000)^1.06 ≈ 5960 s
        assertThat(half.seconds()).isBetween(5900, 6020);
        assertThat(half.basedOn()).isEqualTo("10 km");
        assertThat(half.paceSecPerKm()).isEqualTo(Math.round(half.seconds() * 1000f / 21097));
    }

    @Test
    void predictions_emptyWithoutAnyRecord() {
        assertThat(AthleteStatsService.predictions(List.of())).isEmpty();
    }

    /* ── Monthly & weekly buckets ──────────────────────────────────────── */

    @Test
    void monthly_bucketsLast12MonthsIncludingEmptyOnes() {
        List<Activity> activities = List.of(
                run(TODAY.minusDays(3), 60, "10.00", 200, 150, 45, "168.0"),
                run(TODAY.minusDays(5), 30, "6.00", 50, 140, 20, "172.0"),
                run(TODAY.minusMonths(2), 120, "24.00", 800, 145, 90, null),
                run(TODAY.minusYears(2), 60, "10.00", 100, 150, 40, null)); // outside window

        List<MonthlyStat> monthly = AthleteStatsService.monthly(activities, TODAY);

        assertThat(monthly).hasSize(12);
        assertThat(monthly.getFirst().month()).isEqualTo("2025-08");
        MonthlyStat current = monthly.getLast();
        assertThat(current.month()).isEqualTo("2026-07");
        assertThat(current.runs()).isEqualTo(2);
        assertThat(current.distanceKm()).isEqualByComparingTo("16.0");
        // pace weighted by distance: 90 min over 16 km = 337.5 s/km
        assertThat(current.avgPaceSecPerKm()).isEqualTo(338);
        // HR weighted by duration: (150×60 + 140×30) / 90
        assertThat(current.avgHr()).isEqualTo(147);
        assertThat(current.avgCadenceSpm()).isEqualByComparingTo("169.3");
        assertThat(current.relativeEffort()).isEqualTo(65);
        // an empty month stays present with zeros
        assertThat(monthly.get(1).runs()).isZero();
        assertThat(monthly.get(1).avgPaceSecPerKm()).isNull();
    }

    @Test
    void weeklyEffort_bucketsIsoWeeks() {
        List<Activity> activities = List.of(
                run(TODAY.minusDays(1), 60, "10.00", 200, 150, 45, null),   // this week
                run(TODAY.minusWeeks(1), 60, "12.00", 300, 148, 52, null)); // last week

        List<WeeklyEffort> weekly = AthleteStatsService.weeklyEffort(activities, TODAY);

        assertThat(weekly).hasSize(16);
        assertThat(weekly.getLast().weekStart()).isEqualTo(LocalDate.of(2026, 7, 6)); // Monday
        assertThat(weekly.getLast().relativeEffort()).isEqualTo(45);
        assertThat(weekly.get(14).relativeEffort()).isEqualTo(52);
    }

    /* ── Full hub assembly ─────────────────────────────────────────────── */

    @Test
    void getHub_assemblesSeasonsTimelineAndTotals() {
        User user = new User("a@b.c", "hash", "Adel");
        when(userService.getById(USER)).thenReturn(user);

        TrainingPlan past = new TrainingPlan(USER, "Saison 2025", "Marathon de Lyon",
                LocalDate.of(2025, 1, 5), LocalDate.of(2025, 4, 12));
        ReflectionTestUtils.setField(past, "id", UUID.randomUUID());
        TrainingPlan current = new TrainingPlan(USER, "SaintéLyon 2026", "SaintéLyon 80 km",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        ReflectionTestUtils.setField(current, "id", UUID.randomUUID());
        when(planRepository.findByUserIdOrderByStartDateDesc(USER)).thenReturn(List.of(current, past));
        when(objectiveRepository.findByPlanIdAndRole(any(), any())).thenAnswer(inv -> {
            UUID planId = inv.getArgument(0);
            TrainingPlan plan = planId.equals(past.getId()) ? past : current;
            return java.util.Optional.of(new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                    plan.getGoal(), plan.getEndDate()));
        });

        Activity thisYear = run(LocalDate.of(2026, 2, 1), 60, "10.00", 100, 150, 40, null);
        Activity lastYear = run(LocalDate.of(2025, 6, 1), 90, "20.00", 700, 145, 80, null);
        when(activityRepository.findByUserId(USER)).thenReturn(List.of(thisYear, lastYear));
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of());

        AthleteHubResponse hub = service().getHub(USER, TODAY);

        assertThat(hub.seasons()).extracting(AthleteHubResponse.Season::timeframe)
                .containsExactly(Timeframe.PAST, Timeframe.CURRENT); // sorted oldest first
        assertThat(hub.totals().year().runs()).isEqualTo(1);
        assertThat(hub.totals().year().distanceKm()).isEqualByComparingTo("10.0");
        assertThat(hub.totals().allTime().runs()).isEqualTo(2);
        assertThat(hub.longestRuns().byDistance().distanceKm()).isEqualByComparingTo("20.00");
        assertThat(hub.longestRuns().byDuration().durationMin()).isEqualTo(90);
        assertThat(hub.predictions()).isEmpty();
        assertThat(hub.sync().stravaConnected()).isFalse();
    }
}
