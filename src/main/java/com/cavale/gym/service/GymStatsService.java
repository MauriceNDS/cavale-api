package com.cavale.gym.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.gym.domain.Exercise;
import com.cavale.gym.domain.Muscle;
import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.dto.ExerciseHistoryResponse;
import com.cavale.gym.dto.ExerciseHistoryResponse.PerformedSet;
import com.cavale.gym.dto.ExerciseHistoryResponse.Point;
import com.cavale.gym.dto.ExerciseHistoryResponse.Session;
import com.cavale.gym.dto.GymStatsResponse;
import com.cavale.gym.dto.GymStatsResponse.ExerciseTrend;
import com.cavale.gym.dto.GymStatsResponse.MuscleVolume;
import com.cavale.gym.dto.GymStatsResponse.PrEntry;
import com.cavale.gym.dto.GymStatsResponse.TrendPoint;
import com.cavale.gym.dto.GymStatsResponse.WeekAdherence;
import com.cavale.gym.dto.GymStatsResponse.WeekTonnage;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.training.domain.Discipline;
import com.cavale.training.domain.PlannedSession;
import com.cavale.training.domain.SessionStatus;
import com.cavale.training.repository.PlannedSessionRepository;

/**
 * Gym progression read model, computed from the immutable set logs:
 * estimated 1RM trends (Epley), weekly tonnage, muscle-group balance,
 * fresh PRs and adherence. One pass over the athlete's finished sets —
 * personal-app scale, no pre-aggregation needed yet.
 */
@Service
public class GymStatsService {

    private static final int TONNAGE_WEEKS = 16;
    private static final int BALANCE_WEEKS = 8;
    private static final int ADHERENCE_WEEKS = 8;
    private static final int PR_WINDOW_DAYS = 60;
    private static final int TREND_EXERCISES = 6;

    private final SetLogRepository setLogRepository;
    private final PlannedSessionRepository sessionRepository;
    private final ExerciseService exerciseService;

    public GymStatsService(SetLogRepository setLogRepository,
                           PlannedSessionRepository sessionRepository,
                           ExerciseService exerciseService) {
        this.setLogRepository = setLogRepository;
        this.sessionRepository = sessionRepository;
        this.exerciseService = exerciseService;
    }

    @Transactional(readOnly = true)
    public GymStatsResponse getStats(UUID userId) {
        return getStats(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public GymStatsResponse getStats(UUID userId, LocalDate today) {
        List<SetLog> sets = setLogRepository
                .findByWorkoutLogUserIdAndWorkoutLogStatusOrderByWorkoutLogStartedAtAsc(
                        userId, WorkoutStatus.FINISHED);

        return new GymStatsResponse(
                oneRmTrends(sets),
                weeklyTonnage(sets, today),
                muscleVolume(sets, today),
                prWall(sets, today),
                adherence(userId, today));
    }

    private static LocalDate day(SetLog set) {
        return LocalDate.ofInstant(set.getWorkoutLog().getStartedAt(), com.cavale.common.AppTime.ZONE);
    }

    /**
     * A set that says something about strength: real work, carrying load.
     * Approach sets are excluded from every statistic — counting them would
     * drag tonnage up while dragging the average load down, and would make
     * logging warm-ups honestly a thing that corrupts your own numbers.
     */
    private static boolean weighted(SetLog set) {
        return !set.isWarmup() && set.getWeightKg() != null
                && set.getReps() != null && set.getReps() > 0;
    }

    /** Any working set at all — including bodyweight reps and timed holds. */
    private static boolean working(SetLog set) {
        return !set.isWarmup();
    }

    /** Best estimated 1RM per training day, for the most-trained lifts. */
    private static List<ExerciseTrend> oneRmTrends(List<SetLog> sets) {
        Map<UUID, List<SetLog>> byExercise = new LinkedHashMap<>();
        for (SetLog set : sets) {
            if (weighted(set)) {
                byExercise.computeIfAbsent(set.getExercise().getId(), k -> new ArrayList<>()).add(set);
            }
        }
        return byExercise.values().stream()
                .sorted(Comparator.comparingInt((List<SetLog> l) -> l.size()).reversed())
                .limit(TREND_EXERCISES)
                .map(exerciseSets -> {
                    Exercise exercise = exerciseSets.getFirst().getExercise();
                    Map<LocalDate, TrendPoint> byDay = new LinkedHashMap<>();
                    for (SetLog set : exerciseSets) {
                        BigDecimal oneRm = OneRepMax.of(set);
                        if (oneRm == null) {
                            continue;
                        }
                        byDay.merge(day(set),
                                new TrendPoint(day(set), set.getWeightKg(), oneRm),
                                (a, b) -> a.estOneRmKg().compareTo(b.estOneRmKg()) >= 0 ? a : b);
                    }
                    return new ExerciseTrend(exercise.getId(), exercise.getName(),
                            exercise.getCategory(), List.copyOf(byDay.values()));
                })
                .toList();
    }

    /** Total kg moved per ISO week, empty weeks included, oldest first. */
    private static List<WeekTonnage> weeklyTonnage(List<SetLog> sets, LocalDate today) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        List<WeekTonnage> weeks = new ArrayList<>();
        for (int i = TONNAGE_WEEKS - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            LocalDate weekEnd = weekStart.plusDays(6);
            List<SetLog> weekSets = sets.stream()
                    .filter(s -> !day(s).isBefore(weekStart) && !day(s).isAfter(weekEnd))
                    .filter(GymStatsService::working)
                    .toList();
            BigDecimal tonnage = weekSets.stream()
                    .filter(GymStatsService::weighted)
                    .map(s -> s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, RoundingMode.HALF_UP);
            // Half this library is bodyweight or timed work, which moves no
            // kilos at all: without these two, a whole gainage session reads
            // as a week where nothing happened.
            int secondsUnderTension = weekSets.stream()
                    .filter(s -> s.getSeconds() != null)
                    .mapToInt(SetLog::getSeconds)
                    .sum();
            long workouts = weekSets.stream().map(s -> s.getWorkoutLog().getId()).distinct().count();
            weeks.add(new WeekTonnage(weekStart, tonnage, weekSets.size(), (int) workouts,
                    secondsUnderTension));
        }
        return weeks;
    }

    /** Where the recent volume lands — a set counts toward every muscle it targets. */
    private static List<MuscleVolume> muscleVolume(List<SetLog> sets, LocalDate today) {
        LocalDate from = today.with(DayOfWeek.MONDAY).minusWeeks(BALANCE_WEEKS - 1);
        Map<Muscle, int[]> setCounts = new LinkedHashMap<>();
        Map<Muscle, BigDecimal> tonnage = new LinkedHashMap<>();
        for (SetLog set : sets) {
            if (day(set).isBefore(from) || !working(set)) {
                continue;
            }
            for (Muscle muscle : set.getExercise().getMuscles()) {
                setCounts.computeIfAbsent(muscle, k -> new int[1])[0]++;
                if (weighted(set)) {
                    tonnage.merge(muscle,
                            set.getWeightKg().multiply(BigDecimal.valueOf(set.getReps())),
                            BigDecimal::add);
                }
            }
        }
        return setCounts.entrySet().stream()
                .map(e -> new MuscleVolume(e.getKey(), e.getValue()[0],
                        tonnage.getOrDefault(e.getKey(), BigDecimal.ZERO)
                                .setScale(0, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparingInt(MuscleVolume::sets).reversed())
                .toList();
    }

    /** Records set in the last {@value PR_WINDOW_DAYS} days, with the beaten mark. */
    private static List<PrEntry> prWall(List<SetLog> sets, LocalDate today) {
        record Key(UUID exerciseId, int reps) {
        }
        Map<Key, PrEntry> best = new LinkedHashMap<>();
        for (SetLog set : sets) { // chronological — later sets challenge earlier bests
            if (!weighted(set)) {
                continue;
            }
            Key key = new Key(set.getExercise().getId(), set.getReps());
            PrEntry current = best.get(key);
            if (current == null || set.getWeightKg().compareTo(current.weightKg()) > 0) {
                best.put(key, new PrEntry(set.getExercise().getId(), set.getExerciseName(),
                        set.getReps(), set.getWeightKg(),
                        current != null ? current.weightKg() : null, day(set)));
            }
        }
        return best.values().stream()
                .filter(pr -> !pr.date().isBefore(today.minusDays(PR_WINDOW_DAYS)))
                .sorted(Comparator.comparing(PrEntry::date).reversed())
                .toList();
    }

    /**
     * Everything ever logged on one lift, grouped by the session it happened
     * in. Warm-ups are kept in the per-session detail — you want to see the
     * ramp you actually did — but never counted in a best or a trend.
     */
    @Transactional(readOnly = true)
    public ExerciseHistoryResponse history(UUID userId, UUID exerciseId) {
        Exercise exercise = exerciseService.getOwned(userId, exerciseId);
        List<SetLog> sets = setLogRepository
                .findByWorkoutLogUserIdAndWorkoutLogStatusOrderByWorkoutLogStartedAtAsc(
                        userId, WorkoutStatus.FINISHED).stream()
                .filter(s -> s.getExercise().getId().equals(exerciseId))
                .toList();

        Map<UUID, List<SetLog>> byWorkout = new LinkedHashMap<>();
        for (SetLog set : sets) {
            byWorkout.computeIfAbsent(set.getWorkoutLog().getId(), k -> new ArrayList<>()).add(set);
        }

        List<Session> sessions = new ArrayList<>();
        List<Point> trend = new ArrayList<>();
        for (List<SetLog> group : byWorkout.values()) {
            SetLog first = group.getFirst();
            List<SetLog> work = group.stream().filter(GymStatsService::working).toList();
            BigDecimal top = work.stream()
                    .filter(s -> s.getWeightKg() != null)
                    .map(SetLog::getWeightKg)
                    .max(BigDecimal::compareTo).orElse(null);
            BigDecimal oneRm = work.stream()
                    .map(OneRepMax::of)
                    .filter(java.util.Objects::nonNull)
                    .max(BigDecimal::compareTo).orElse(null);
            BigDecimal tonnage = work.stream()
                    .filter(GymStatsService::weighted)
                    .map(s -> s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, RoundingMode.HALF_UP);
            sessions.add(new Session(first.getWorkoutLog().getId(), day(first),
                    first.getWorkoutLog().getTemplateName(),
                    group.stream()
                            .sorted(Comparator.comparingInt(SetLog::getSetNumber))
                            .map(s -> new PerformedSet(s.getSetNumber(), s.getReps(),
                                    s.getWeightKg(), s.getSeconds(), s.isWarmup(), s.getRir()))
                            .toList(),
                    top, oneRm, tonnage));
            if (oneRm != null) {
                trend.add(new Point(day(first), oneRm));
            }
        }

        BigDecimal bestOneRm = trend.stream().map(Point::estOneRmKg)
                .max(BigDecimal::compareTo).orElse(null);
        BigDecimal bestWeight = sets.stream().filter(GymStatsService::weighted)
                .map(SetLog::getWeightKg).max(BigDecimal::compareTo).orElse(null);

        // how long the lift has been standing still
        Integer stalled = null;
        if (bestOneRm != null) {
            int lastPeak = 0;
            for (int i = 0; i < trend.size(); i++) {
                if (trend.get(i).estOneRmKg().compareTo(bestOneRm) >= 0) {
                    lastPeak = i;
                }
            }
            stalled = trend.size() - 1 - lastPeak;
        }

        List<Session> newestFirst = new ArrayList<>(sessions);
        java.util.Collections.reverse(newestFirst);
        return new ExerciseHistoryResponse(exercise.getId(), exercise.getName(),
                List.copyOf(newestFirst), List.copyOf(trend), bestWeight, bestOneRm,
                (int) sets.stream().filter(GymStatsService::working).count(), stalled);
    }

    /** Planned vs done GYM sessions per week — is the strength work happening? */
    private List<WeekAdherence> adherence(UUID userId, LocalDate today) {
        LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
        LocalDate from = currentWeekStart.minusWeeks(ADHERENCE_WEEKS - 1);
        List<PlannedSession> sessions = sessionRepository
                .findByUserIdAndDateBetweenOrderByDateAscOrderInDayAsc(userId, from,
                        currentWeekStart.plusDays(6)).stream()
                .filter(s -> s.getDiscipline() == Discipline.GYM)
                .toList();

        List<WeekAdherence> weeks = new ArrayList<>();
        for (int i = ADHERENCE_WEEKS - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            LocalDate weekEnd = weekStart.plusDays(6);
            List<PlannedSession> weekSessions = sessions.stream()
                    .filter(s -> !s.getDate().isBefore(weekStart) && !s.getDate().isAfter(weekEnd))
                    .toList();
            weeks.add(new WeekAdherence(weekStart, weekSessions.size(),
                    (int) weekSessions.stream()
                            .filter(s -> s.getStatus() == SessionStatus.DONE).count()));
        }
        return weeks;
    }
}
