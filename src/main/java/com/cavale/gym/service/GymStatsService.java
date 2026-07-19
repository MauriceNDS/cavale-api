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

    public GymStatsService(SetLogRepository setLogRepository,
                           PlannedSessionRepository sessionRepository) {
        this.setLogRepository = setLogRepository;
        this.sessionRepository = sessionRepository;
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

    /** Epley: 1RM ≈ w × (1 + reps/30). The classic, good enough to trend. */
    static BigDecimal estimateOneRm(BigDecimal weightKg, int reps) {
        return weightKg.multiply(BigDecimal.ONE.add(
                        BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP)))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private static LocalDate day(SetLog set) {
        return LocalDate.ofInstant(set.getWorkoutLog().getStartedAt(), com.cavale.common.AppTime.ZONE);
    }

    private static boolean weighted(SetLog set) {
        return set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0;
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
                        BigDecimal oneRm = estimateOneRm(set.getWeightKg(), set.getReps());
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
                    .toList();
            BigDecimal tonnage = weekSets.stream()
                    .filter(GymStatsService::weighted)
                    .map(s -> s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, RoundingMode.HALF_UP);
            long workouts = weekSets.stream().map(s -> s.getWorkoutLog().getId()).distinct().count();
            weeks.add(new WeekTonnage(weekStart, tonnage, weekSets.size(), (int) workouts));
        }
        return weeks;
    }

    /** Where the recent volume lands — a set counts toward every muscle it targets. */
    private static List<MuscleVolume> muscleVolume(List<SetLog> sets, LocalDate today) {
        LocalDate from = today.with(DayOfWeek.MONDAY).minusWeeks(BALANCE_WEEKS - 1);
        Map<Muscle, int[]> setCounts = new LinkedHashMap<>();
        Map<Muscle, BigDecimal> tonnage = new LinkedHashMap<>();
        for (SetLog set : sets) {
            if (day(set).isBefore(from)) {
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
