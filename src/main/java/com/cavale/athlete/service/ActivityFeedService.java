package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.athlete.dto.ActivityFeedResponse;
import com.cavale.athlete.dto.ActivityFeedResponse.FeedItem;
import com.cavale.athlete.dto.ActivityFeedResponse.FeedType;
import com.cavale.gym.domain.SetLog;
import com.cavale.gym.domain.WorkoutLog;
import com.cavale.gym.domain.WorkoutStatus;
import com.cavale.gym.repository.SetLogRepository;
import com.cavale.gym.repository.WorkoutLogRepository;
import com.cavale.training.domain.Activity;
import com.cavale.training.repository.ActivityRepository;

/**
 * The unified history feed. Runs and gym workouts live in different tables,
 * so a page is assembled by over-fetching (page+1 pages from each source),
 * merging by date and slicing — O(page) per request, fine at personal scale
 * and honest about hasMore.
 */
@Service
public class ActivityFeedService {

    private final ActivityRepository activityRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final SetLogRepository setLogRepository;

    public ActivityFeedService(ActivityRepository activityRepository,
                               WorkoutLogRepository workoutLogRepository,
                               SetLogRepository setLogRepository) {
        this.activityRepository = activityRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.setLogRepository = setLogRepository;
    }

    /** Search knobs: q matches the run/session/template name, dates bound the range. */
    @Transactional(readOnly = true)
    public ActivityFeedResponse feed(UUID userId, FeedType type, String q,
                                     LocalDate from, LocalDate to, int page, int size) {
        int fetch = (page + 1) * size + 1;
        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.of(9999, 12, 31);

        var runPage = type == FeedType.GYM ? null
                : activityRepository.search(userId, pattern, fromDate, toDate, type == FeedType.RUN,
                        PageRequest.of(0, fetch, Sort.by(Sort.Direction.DESC, "date")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))));
        var workoutPage = type == FeedType.RUN ? null
                : workoutLogRepository.search(userId, WorkoutStatus.FINISHED, pattern,
                        fromDate.atStartOfDay(com.cavale.common.AppTime.ZONE).toInstant(),
                        toDate.plusDays(1).atStartOfDay(com.cavale.common.AppTime.ZONE).toInstant(),
                        PageRequest.of(0, fetch, Sort.by(Sort.Direction.DESC, "startedAt")));

        List<FeedItem> merged = new java.util.ArrayList<>();
        if (runPage != null) {
            runPage.getContent().forEach(a -> merged.add(runItem(a)));
        }
        if (workoutPage != null) {
            merged.addAll(workoutItems(workoutPage.getContent()));
        }
        merged.sort(Comparator.comparing(FeedItem::date).reversed());

        long total = (runPage != null ? runPage.getTotalElements() : 0)
                + (workoutPage != null ? workoutPage.getTotalElements() : 0);
        int start = Math.min(page * size, merged.size());
        int end = Math.min(start + size, merged.size());
        return new ActivityFeedResponse(merged.subList(start, end), page,
                (long) (page + 1) * size < total, total);
    }

    private static FeedItem runItem(Activity activity) {
        Integer pace = null;
        if (activity.getDistanceKm() != null && activity.getDistanceKm().signum() > 0) {
            pace = (int) Math.round(activity.getDurationMin() * 60
                    / activity.getDistanceKm().doubleValue());
        }
        return new FeedItem(activity.getId(),
                activity.isRun() ? FeedType.RUN : FeedType.BIKE, activity.getDate(),
                activity.getSession() != null ? activity.getSession().getTitle() : activity.getName(),
                activity.getDurationMin(), activity.getPerceivedEffort(), activity.isPainFlag(),
                activity.getDistanceKm(), activity.getElevationM(), activity.getAvgHr(), pace,
                activity.getSource(),
                activity.getSession() != null ? activity.getSession().getId() : null,
                null, null, null);
    }

    private List<FeedItem> workoutItems(List<WorkoutLog> logs) {
        Map<UUID, List<SetLog>> setsByLog = setLogRepository
                .findByWorkoutLogIdIn(logs.stream().map(WorkoutLog::getId).toList()).stream()
                .collect(Collectors.groupingBy(s -> s.getWorkoutLog().getId()));

        return logs.stream().map(log -> {
            List<SetLog> sets = setsByLog.getOrDefault(log.getId(), List.of());
            BigDecimal tonnage = sets.stream()
                    .filter(s -> s.getWeightKg() != null && s.getReps() != null)
                    .map(s -> s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, RoundingMode.HALF_UP);
            return new FeedItem(log.getId(), FeedType.GYM,
                    LocalDate.ofInstant(log.getStartedAt(), com.cavale.common.AppTime.ZONE),
                    log.getTemplateName() != null ? log.getTemplateName() : "Renfo",
                    log.getDurationMin(), log.getPerceivedEffort(), log.isPainFlag(),
                    null, null, null, null, null,
                    log.getSession() != null ? log.getSession().getId() : null,
                    log.getTemplateName(), tonnage, sets.size());
        }).toList();
    }
}
