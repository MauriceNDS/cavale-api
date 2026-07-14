package com.cavale.athlete.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Transactional(readOnly = true)
    public ActivityFeedResponse feed(UUID userId, FeedType type, int page, int size) {
        int fetch = (page + 1) * size + 1; // +1: an honest hasMore without counts

        List<FeedItem> runs = type == FeedType.GYM ? List.of() : runs(userId, fetch);
        List<FeedItem> workouts = type == FeedType.RUN ? List.of() : workouts(userId, fetch);

        List<FeedItem> merged = new java.util.ArrayList<>(runs);
        merged.addAll(workouts);
        merged.sort(Comparator.comparing(FeedItem::date).reversed());

        int from = Math.min(page * size, merged.size());
        int to = Math.min(from + size, merged.size());
        return new ActivityFeedResponse(merged.subList(from, to), page, merged.size() > to);
    }

    private List<FeedItem> runs(UUID userId, int fetch) {
        Pageable pageable = PageRequest.of(0, fetch,
                Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return activityRepository.findByUserId(userId, pageable).getContent().stream()
                .map(ActivityFeedService::runItem)
                .toList();
    }

    private static FeedItem runItem(Activity activity) {
        Integer pace = null;
        if (activity.getDistanceKm() != null && activity.getDistanceKm().signum() > 0) {
            pace = (int) Math.round(activity.getDurationMin() * 60
                    / activity.getDistanceKm().doubleValue());
        }
        return new FeedItem(activity.getId(), FeedType.RUN, activity.getDate(),
                activity.getSession() != null ? activity.getSession().getTitle() : activity.getName(),
                activity.getDurationMin(), activity.getPerceivedEffort(), activity.isPainFlag(),
                activity.getDistanceKm(), activity.getElevationM(), activity.getAvgHr(), pace,
                activity.getSource(),
                activity.getSession() != null ? activity.getSession().getId() : null,
                null, null, null);
    }

    private List<FeedItem> workouts(UUID userId, int fetch) {
        Pageable pageable = PageRequest.of(0, fetch, Sort.by(Sort.Direction.DESC, "startedAt"));
        List<WorkoutLog> logs = workoutLogRepository
                .findByUserIdAndStatus(userId, WorkoutStatus.FINISHED, pageable).getContent();

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
                    LocalDate.ofInstant(log.getStartedAt(), ZoneId.systemDefault()),
                    log.getTemplateName() != null ? log.getTemplateName() : "Renfo",
                    log.getDurationMin(), log.getPerceivedEffort(), log.isPainFlag(),
                    null, null, null, null, null,
                    log.getSession() != null ? log.getSession().getId() : null,
                    log.getTemplateName(), tonnage, sets.size());
        }).toList();
    }
}
