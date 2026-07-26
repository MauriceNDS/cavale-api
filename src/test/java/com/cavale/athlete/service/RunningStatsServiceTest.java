package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cavale.athlete.dto.RunningStatsResponse;
import com.cavale.athlete.dto.RunningStatsResponse.Acwr;
import com.cavale.athlete.dto.RunningStatsResponse.AcwrZone;
import com.cavale.athlete.dto.RunningStatsResponse.DayForm;
import com.cavale.athlete.dto.RunningStatsResponse.TrainingStatusLabel;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.ActivityBestEffort;
import com.cavale.training.domain.Discipline;
import com.cavale.training.repository.ActivityBestEffortRepository;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ObjectiveRepository;
import com.cavale.user.domain.User;
import com.cavale.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunningStatsServiceTest {

    private static final UUID USER = UUID.randomUUID();
    /** A Monday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityBestEffortRepository bestEffortRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private UserService userService;

    private RunningStatsService service() {
        lenient().when(userService.getById(USER)).thenReturn(userWithHr());
        return new RunningStatsService(activityRepository, bestEffortRepository,
                objectiveRepository, userService);
    }

    /** An athlete with HR zones set, so VO2max estimates can be computed. */
    private static User userWithHr() {
        User user = new User("a@b.c", "hash", "Arsène");
        user.updateProfile("Arsène", null, null, null, 190, 50);
        return user;
    }

    private static Activity run(LocalDate date, int durationMin, String km, Integer elevationM,
                                Integer avgHr, Integer relativeEffort, long externalId) {
        Activity activity = Activity.stravaHistory(USER, date, durationMin, new BigDecimal(km),
                elevationM, avgHr, "Sortie", externalId);
        activity.enrich(null, relativeEffort, null);
        return activity;
    }

    /** A cross-training bike ride: no HR, no relative effort (duration-based load). */
    private static Activity bike(LocalDate date, int durationMin, String km, long externalId) {
        Activity activity = Activity.stravaHistory(USER, date, durationMin, new BigDecimal(km),
                null, null, "Vélo", externalId);
        activity.markDiscipline(Discipline.CROSS);
        return activity;
    }

    private RunningStatsResponse stats(List<Activity> activities) {
        when(activityRepository.findByUserId(USER)).thenReturn(activities);
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of());
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of());
        return service().getStats(USER, TODAY);
    }

    @Test
    void form_buildsContinuousDailyCurves() {
        RunningStatsResponse stats = stats(List.of(
                run(TODAY.minusDays(1), 60, "10.0", 200, 150, 60, 1L)));

        assertThat(stats.form()).hasSize(RunningStatsService.FORM_DAYS);
        var yesterday = stats.form().get(stats.form().size() - 2);
        assertThat(yesterday.date()).isEqualTo(TODAY.minusDays(1));
        assertThat(yesterday.fatigue()).isGreaterThan(yesterday.fitness()); // fresh spike
        assertThat(yesterday.formScore()).isNegative();
        // both decay the day after
        assertThat(stats.form().getLast().fatigue()).isLessThan(yesterday.fatigue());
    }

    @Test
    void weeklyEffort_bandComesFromTrailingThreeWeeks() {
        RunningStatsResponse stats = stats(List.of(
                run(TODAY.minusWeeks(3), 60, "10.0", 100, 150, 90, 1L),
                run(TODAY.minusWeeks(2), 60, "10.0", 100, 150, 100, 2L),
                run(TODAY.minusWeeks(1), 60, "10.0", 100, 150, 110, 3L),
                run(TODAY, 60, "10.0", 100, 150, 50, 4L)));

        var current = stats.weeklyEffort().getLast();
        assertThat(current.weekStart()).isEqualTo(TODAY);
        assertThat(current.effort()).isEqualTo(50);
        // trailing average (90+100+110)/3 = 100 → band 80–130
        assertThat(current.bandLow()).isEqualTo(80);
        assertThat(current.bandHigh()).isEqualTo(130);
        assertThat(current.partlyEstimated()).isFalse();
    }

    @Test
    void hrLessRuns_getEstimatedEffortAndAreFlagged() {
        RunningStatsResponse stats = stats(List.of(
                run(TODAY, 60, "10.0", 100, null, null, 1L))); // no HR, no RE

        var current = stats.weeklyEffort().getLast();
        assertThat(current.effort()).isEqualTo(42); // 60 min × 0.7
        assertThat(current.partlyEstimated()).isTrue();
    }

    @Test
    void acwr_flagsASuddenSpike() {
        // 3 calm weeks then a monster week
        RunningStatsResponse stats = stats(List.of(
                run(TODAY.minusDays(25), 60, "10.0", 100, 150, 50, 1L),
                run(TODAY.minusDays(18), 60, "10.0", 100, 150, 50, 2L),
                run(TODAY.minusDays(11), 60, "10.0", 100, 150, 50, 3L),
                run(TODAY.minusDays(2), 120, "24.0", 600, 155, 200, 4L),
                run(TODAY.minusDays(1), 90, "16.0", 500, 155, 150, 5L)));

        assertThat(stats.acwr().acute7d()).isEqualTo(350);
        assertThat(stats.acwr().ratio()).isGreaterThan(1.5);
        assertThat(stats.acwr().zone()).isEqualTo(AcwrZone.DANGER);
    }

    @Test
    void weeklyVolume_carriesKmEffort() {
        RunningStatsResponse stats = stats(List.of(
                run(TODAY, 120, "20.0", 800, 150, 120, 1L)));

        var week = stats.weeklyVolume().getLast();
        assertThat(week.distanceKm()).isEqualByComparingTo("20.0");
        assertThat(week.elevationM()).isEqualTo(800);
        assertThat(week.kmEffort()).isEqualByComparingTo("28.0"); // 20 + 800/100
    }

    @Test
    void checkpoints_useWholeRunsNearTheMark() {
        RunningStatsResponse stats = stats(List.of(
                run(TODAY.minusDays(1), 58, "10.0", 300, 150, 60, 1L),
                run(TODAY.minusDays(8), 62, "10.6", 340, 150, 60, 2L),
                run(TODAY.minusDays(15), 61, "10.4", 320, 150, 60, 3L)));

        var oneHour = stats.checkpoints().stream()
                .filter(c -> c.minutes() == 60).findFirst().orElseThrow();
        assertThat(oneHour.samples()).isEqualTo(3);
        assertThat(oneHour.medianDistanceKm()).isEqualByComparingTo("10.4");
        assertThat(oneHour.medianElevationM()).isEqualTo(320);
    }

    @Test
    void checkpoints_readStreamsWhenAvailable() {
        // 2 h run whose streams say: at 1 h → 8 km, ~250 m D+ climbed
        Activity longRun = run(TODAY.minusDays(1), 120, "18.0", 500, 150, 120, 1L);
        longRun.attachStreams("""
                {"time":[0,1800,3600,5400,7200],
                 "distance":[0,4200,8000,13000,18000],
                 "alt":[600,750,850,700,600]}""");

        RunningStatsResponse stats = stats(List.of(longRun));

        var oneHour = stats.checkpoints().stream()
                .filter(c -> c.minutes() == 60).findFirst().orElseThrow();
        assertThat(oneHour.samples()).isEqualTo(1);
        assertThat(oneHour.medianDistanceKm()).isEqualByComparingTo("8.0");
        assertThat(oneHour.medianElevationM()).isEqualTo(250); // 600→750→850
    }

    @Test
    void vickersExponent_growsAsMileageDrops() {
        assertThat(RunningStatsService.vickersExponent(90)).isEqualTo(1.06);
        assertThat(RunningStatsService.vickersExponent(40)).isEqualTo(1.08);
        assertThat(RunningStatsService.vickersExponent(0)).isEqualTo(1.10); // capped
    }

    @Test
    void trailEstimates_scaleTheAthletesOwnPacePerKmEffort() {
        // three hilly long runs: 90 min for 12 km + 600 D+ → 18 km-effort → 300 s/KE
        List<Activity> runs = List.of(
                run(TODAY.minusDays(7), 90, "12.0", 600, 150, 100, 1L),
                run(TODAY.minusDays(14), 90, "12.0", 600, 150, 100, 2L),
                run(TODAY.minusDays(21), 90, "12.0", 600, 150, 100, 3L));
        var plan = new com.cavale.training.domain.TrainingPlan(USER, "Saison", null,
                TODAY.minusDays(30), TODAY.plusDays(120));
        var objective = new com.cavale.training.domain.Objective(plan,
                com.cavale.training.domain.ObjectiveRole.MAIN,
                com.cavale.training.domain.ObjectiveType.RACE, "SaintéLyon", TODAY.plusDays(90));
        objective.updateRaceProfile(new BigDecimal("80.00"), 2100, "Lyon");

        when(activityRepository.findByUserId(USER)).thenReturn(runs);
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of());
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of(objective));

        var estimates = service().getStats(USER, TODAY).trailEstimates();

        assertThat(estimates).hasSize(1);
        var sainteLyon = estimates.getFirst();
        assertThat(sainteLyon.kmEffort()).isEqualByComparingTo("101"); // 80 + 2100/100
        // 300 s/KE × 101 KE ≈ 8h25 before fatigue; the ultra term adds ~12 %
        assertThat(sainteLyon.midSec()).isBetween(30000, 36000);
        assertThat(sainteLyon.lowSec()).isLessThanOrEqualTo(sainteLyon.midSec());
        assertThat(sainteLyon.highSec()).isGreaterThanOrEqualTo(sainteLyon.midSec());
    }

    /* ── Cross-training (bike) ─────────────────────────────────────────── */

    @Test
    void crossTraining_feedsLoadButIsExcludedFromRunVolume() {
        // a 60-min bike ride (no HR/RE) alongside a run in the same week
        RunningStatsResponse stats = stats(List.of(
                run(TODAY, 45, "9.0", 100, 145, 40, 1L),
                bike(TODAY.plusDays(1), 60, "25.0", 2L)));

        // load counts the bike: 40 (run) + 60×0.7 (bike estimate) = 82
        assertThat(stats.weeklyEffort().getLast().effort()).isEqualTo(82);
        // run volume ignores the bike: only the 9 km run counts
        assertThat(stats.weeklyVolume().getLast().distanceKm()).isEqualByComparingTo("9.0");
        assertThat(stats.weeklyVolume().getLast().runs()).isEqualTo(1);
    }

    /* ── Monotony & strain (Foster) ────────────────────────────────────── */

    @Test
    void monotony_flagsAGrindingWeekAndSparesAVariedOne() {
        LocalDate thisMonday = TODAY; // TODAY is a Monday
        LocalDate prevMonday = thisMonday.minusWeeks(1);
        List<Activity> activities = new ArrayList<>();
        // last week: the same easy run every single day → near-flat load, high monotony
        for (int d = 0; d < 7; d++) {
            activities.add(run(prevMonday.plusDays(d), 45, "8.0", 100, 140, 45 + d % 2, 10L + d));
        }
        // this week: one hard day, one easy day, the rest off → strong contrast
        activities.add(run(thisMonday, 90, "18.0", 500, 155, 150, 1L));
        activities.add(run(thisMonday.plusDays(3), 30, "6.0", 50, 130, 30, 2L));

        List<RunningStatsResponse.WeekMonotony> series = stats(activities).monotony();
        assertThat(series).hasSize(52);

        var grindWeek = series.stream().filter(w -> w.weekStart().equals(prevMonday))
                .findFirst().orElseThrow();
        assertThat(grindWeek.monotony()).isGreaterThanOrEqualTo(2.0);
        assertThat(grindWeek.flagged()).isTrue();
        assertThat(grindWeek.strain()).isNotNull();

        var variedWeek = series.stream().filter(w -> w.weekStart().equals(thisMonday))
                .findFirst().orElseThrow();
        assertThat(variedWeek.monotony()).isLessThan(2.0);
        assertThat(variedWeek.flagged()).isFalse();
    }

    @Test
    void monotony_isNullForARestWeek() {
        // a lone run five weeks ago — this week has no training at all
        List<RunningStatsResponse.WeekMonotony> series = stats(List.of(
                run(TODAY.minusWeeks(5).plusDays(1), 60, "10.0", 100, 140, 50, 1L))).monotony();

        assertThat(series.getLast().weekStart()).isEqualTo(TODAY);
        assertThat(series.getLast().monotony()).isNull();
        assertThat(series.getLast().strain()).isNull();
        assertThat(series.getLast().flagged()).isFalse();
    }

    /* ── Training-status verdict ────────────────────────────────────────── */

    @Test
    void trainingStatus_ladderIsDeterministic() {
        assertThat(RunningStatsService.trainingStatus(form(100, 110, 5),
                acwr(1.0, AcwrZone.OPTIMAL)).label()).isEqualTo(TrainingStatusLabel.PRODUCTIVE);
        assertThat(RunningStatsService.trainingStatus(form(100, 100, 5),
                acwr(1.6, AcwrZone.DANGER)).label()).isEqualTo(TrainingStatusLabel.OVERREACHING);
        assertThat(RunningStatsService.trainingStatus(form(100, 100, -5),
                acwr(1.35, AcwrZone.CAUTION)).label()).isEqualTo(TrainingStatusLabel.OVERREACHING);
        assertThat(RunningStatsService.trainingStatus(form(100, 90, 10),
                acwr(0.6, AcwrZone.UNDER)).label()).isEqualTo(TrainingStatusLabel.RECOVERY);
        assertThat(RunningStatsService.trainingStatus(form(100, 90, -5),
                acwr(0.6, AcwrZone.UNDER)).label()).isEqualTo(TrainingStatusLabel.DETRAINING);
        assertThat(RunningStatsService.trainingStatus(form(100, 100, -2),
                acwr(1.0, AcwrZone.OPTIMAL)).label()).isEqualTo(TrainingStatusLabel.MAINTAINING);
    }

    @Test
    void trainingStatus_flagsOverreachingFromTheRealCurves() {
        RunningStatsResponse stats = stats(List.of(
                run(TODAY.minusDays(25), 60, "10.0", 100, 150, 50, 1L),
                run(TODAY.minusDays(18), 60, "10.0", 100, 150, 50, 2L),
                run(TODAY.minusDays(11), 60, "10.0", 100, 150, 50, 3L),
                run(TODAY.minusDays(2), 120, "24.0", 600, 155, 200, 4L),
                run(TODAY.minusDays(1), 90, "16.0", 500, 155, 150, 5L)));

        assertThat(stats.trainingStatus().label()).isEqualTo(TrainingStatusLabel.OVERREACHING);
        assertThat(stats.trainingStatus().acwr()).isEqualTo(stats.acwr().ratio());
    }

    /* ── Effective VO2max & critical pace (P5) ─────────────────────────── */

    @Test
    void effectiveVo2max_scalesDanielsCostByHeartRateReserve() {
        // 10 km in 50 min → 200 m/min; HR 155 of (190 max, 50 rest) → 75 % HRR
        Double vo2max = RunningStatsService.effectiveVo2max(
                run(TODAY, 50, "10.0", 50, 155, 80, 1L), 190, 50);
        assertThat(vo2max).isNotNull();
        // Daniels VO2 ≈ 36.0; 3.5 + (36.0 − 3.5) / 0.75 ≈ 46.8
        assertThat(vo2max).isBetween(44.0, 50.0);
    }

    @Test
    void effectiveVo2max_nullForHillyOrEasyRuns() {
        // too hilly: 600 m over 10 km = 60 m/km
        assertThat(RunningStatsService.effectiveVo2max(
                run(TODAY, 60, "10.0", 600, 150, 80, 1L), 190, 50)).isNull();
        // too easy: HR 110 → 43 % HRR, under the aerobic floor
        assertThat(RunningStatsService.effectiveVo2max(
                run(TODAY, 50, "10.0", 30, 110, 40, 2L), 190, 50)).isNull();
    }

    @Test
    void vo2maxTrend_reportsMonthlyMediansFromRoadRuns() {
        RunningStatsResponse stats = stats(List.of(
                run(TODAY.minusDays(3), 50, "10.0", 50, 155, 80, 1L),
                run(TODAY.minusDays(10), 45, "9.0", 40, 150, 70, 2L)));

        assertThat(stats.vo2maxTrend()).hasSize(12);
        var currentMonth = stats.vo2maxTrend().getLast();
        assertThat(currentMonth.vo2max()).isNotNull();
        assertThat(currentMonth.runs()).isEqualTo(2);
    }

    @Test
    void criticalPace_fitsTheBestEffortCurve() {
        Activity flat = run(TODAY.minusDays(5), 40, "10.0", 50, 150, 60, 1L); // road-like parent
        when(activityRepository.findByUserId(USER)).thenReturn(List.of(flat));
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of());
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of(
                new ActivityBestEffort(flat, "1k", 1000, 200),
                new ActivityBestEffort(flat, "5k", 5000, 1100),
                new ActivityBestEffort(flat, "10k", 10000, 2400)));

        var criticalPace = service().getStats(USER, TODAY).criticalPace();
        assertThat(criticalPace).isNotNull();
        assertThat(criticalPace.samples()).isEqualTo(3);
        assertThat(criticalPace.criticalSpeedMps()).isBetween(3.5, 5.0);
        assertThat(criticalPace.criticalPaceSecPerKm()).isBetween(200, 290);
    }

    @Test
    void criticalPace_nullWithoutEnoughDistances() {
        Activity flat = run(TODAY.minusDays(5), 40, "10.0", 50, 150, 60, 1L);
        when(activityRepository.findByUserId(USER)).thenReturn(List.of(flat));
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of());
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of(
                new ActivityBestEffort(flat, "1k", 1000, 200),
                new ActivityBestEffort(flat, "5k", 5000, 1100)));

        assertThat(service().getStats(USER, TODAY).criticalPace()).isNull();
    }

    /* ── Aerobic durability / late-run fade (P6) ───────────────────────── */

    @Test
    void decoupling_detectsLateRunFade() {
        // even splits by distance, but HR climbs in the second half → positive decoupling
        Double decoupling = RunningStatsService.decoupling("""
                {"time":[0,900,1800,2700,3600],
                 "distance":[0,2500,5000,7500,10000],
                 "hr":[138,138,138,160,160]}""");

        assertThat(decoupling).isNotNull();
        assertThat(decoupling).isGreaterThan(3.0);
    }

    @Test
    void durability_reportsDecouplingForLongRunsWithStreamsOnly() {
        Activity longRun = run(TODAY.minusDays(4), 120, "22.0", 400, 150, 130, 1L);
        longRun.attachStreams("""
                {"time":[0,1800,3600,5400,7200],
                 "distance":[0,5500,11000,16000,21000],
                 "hr":[140,140,140,158,158]}""");
        Activity shortRun = run(TODAY.minusDays(2), 45, "9.0", 100, 145, 50, 2L); // under 90 min

        RunningStatsResponse stats = stats(List.of(longRun, shortRun));

        assertThat(stats.durability()).hasSize(1);
        assertThat(stats.durability().getFirst().durationMin()).isEqualTo(120);
        assertThat(stats.durability().getFirst().distanceKm()).isEqualByComparingTo("22.0");
        assertThat(stats.durability().getFirst().decouplingPct()).isGreaterThan(0.0);
    }

    /** A 30-day form window with a controlled fitness trend and current form. */
    private static List<DayForm> form(double pastFitness, double nowFitness, double nowForm) {
        List<DayForm> series = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            series.add(new DayForm(TODAY.minusDays(29L - i), 0, 0, 0));
        }
        series.set(1, new DayForm(TODAY.minusDays(28), pastFitness, 0, 0));
        series.set(29, new DayForm(TODAY, nowFitness, nowFitness - nowForm, nowForm));
        return series;
    }

    private static Acwr acwr(double ratio, AcwrZone zone) {
        return new Acwr(ratio, 0, 0, zone);
    }

    @Test
    void allTimeWindow_stretchesSeriesBackToTheFirstActivity() {
        LocalDate ancient = TODAY.minusYears(2);
        List<Activity> activities = List.of(
                run(ancient, 60, "10.0", 100, 150, 80, 1L),
                run(TODAY.minusDays(1), 60, "10.0", 100, 150, 60, 2L));
        when(activityRepository.findByUserId(USER)).thenReturn(activities);
        when(bestEffortRepository.findByUserId(USER)).thenReturn(List.of());
        when(objectiveRepository.findByUserId(USER)).thenReturn(List.of());
        RunningStatsService service = service();

        RunningStatsResponse defaults = service.getStats(USER, TODAY, null);
        RunningStatsResponse allTime = service.getStats(USER, TODAY, 0);

        // default window misses the 2-year-old run entirely
        assertThat(defaults.weeklyVolume().getFirst().weekStart()).isAfter(ancient);
        assertThat(defaults.form()).hasSize(RunningStatsService.FORM_DAYS);

        // all-time reaches back to (at least) the first activity's week
        assertThat(allTime.weeklyVolume().getFirst().weekStart())
                .isBeforeOrEqualTo(ancient);
        assertThat(allTime.weeklyVolume().stream()
                .filter(w -> !w.weekStart().isAfter(ancient))
                .anyMatch(w -> w.runs() == 1)).isTrue();
        assertThat(allTime.form().size()).isGreaterThan(RunningStatsService.FORM_DAYS);
        assertThat(allTime.form().getFirst().date()).isBeforeOrEqualTo(ancient);
        // current-state blocks are unaffected by the window
        assertThat(allTime.acwr()).isEqualTo(defaults.acwr());
    }
}
