package com.cavale.gym.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.AppTime;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.repository.WorkoutLogRepository;
import com.cavale.training.domain.PerceivedEffort;

/**
 * What a strength session is worth, in the same currency as a run.
 *
 * <p>A hard leg day is real fatigue and used to be invisible: the load
 * curves, the ACWR and the training-status verdict were built from
 * activities alone, so the app read a max-strength session as a rest day.
 *
 * <p>The conversion is deliberately NOT Foster's session-RPE. Raw sRPE
 * (RPE × minutes) lands around 6 points per minute, while the relative
 * effort this athlete's runs actually produce sits between 0.5 and 1.2 per
 * minute — so sRPE would make a 40-minute gym session outweigh three long
 * runs and wreck every downstream number. Instead the perceived effort
 * picks a per-minute rate inside that same band, which keeps a strength
 * session a real but modest contribution.
 */
@Service
public class GymLoadService {

    /**
     * Relative-effort points per minute, by how the session felt. The band
     * mirrors what real runs produce; {@code COMME_PREVU} deliberately sits
     * on {@code 0.70}, the same figure the app already uses to estimate a
     * run with no heart-rate data.
     */
    static double ratePerMinute(PerceivedEffort effort) {
        if (effort == null) {
            return 0.70;
        }
        return switch (effort) {
            case TROP_FACILE -> 0.40;
            case FACILE -> 0.55;
            case COMME_PREVU -> 0.70;
            case DIFFICILE -> 0.85;
            case TROP_DIFFICILE -> 1.00;
        };
    }

    /** The load one finished workout contributes — 0 while it is still running. */
    public static int loadOf(WorkoutLog log) {
        if (log.getStatus() != WorkoutStatus.FINISHED || log.getDurationMin() == null) {
            return 0;
        }
        return (int) Math.round(log.getDurationMin() * ratePerMinute(log.getPerceivedEffort()));
    }

    private final WorkoutLogRepository workoutLogRepository;

    public GymLoadService(WorkoutLogRepository workoutLogRepository) {
        this.workoutLogRepository = workoutLogRepository;
    }

    /**
     * Strength load per calendar day, for merging into the athlete's load
     * curves. Days without a workout are simply absent.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Integer> dailyLoad(UUID userId) {
        Map<LocalDate, Integer> byDay = new HashMap<>();
        for (WorkoutLog log : workoutLogRepository.findByUserIdAndStatusOrderByStartedAtDesc(
                userId, WorkoutStatus.FINISHED)) {
            int load = loadOf(log);
            if (load > 0) {
                byDay.merge(LocalDate.ofInstant(log.getStartedAt(), AppTime.ZONE), load,
                        Integer::sum);
            }
        }
        return byDay;
    }
}
