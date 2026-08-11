package com.cavale.training.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.AppTime;
import com.cavale.common.Strings;
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Activity;
import com.cavale.training.domain.Shoe;
import com.cavale.training.dto.ShoeOverviewResponse;
import com.cavale.training.dto.ShoeRequest;
import com.cavale.training.dto.ShoeResponse;
import com.cavale.training.dto.ShoeStatsResponse;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.ShoeRepository;

/**
 * The athlete's shoe rotation. Mileage is never stored — every read sums the
 * distance of the activities linked to each pair, so it stays correct through
 * edits and re-validations.
 */
@Service
public class ShoeService {

    private final ShoeRepository shoeRepository;
    private final ActivityRepository activityRepository;

    public ShoeService(ShoeRepository shoeRepository, ActivityRepository activityRepository) {
        this.shoeRepository = shoeRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public List<ShoeResponse> list(UUID userId) {
        Map<UUID, BigDecimal> mileage = mileageByShoe(userId);
        return shoeRepository.findByUserIdOrderByRetiredAscCreatedAtDesc(userId).stream()
                .map(shoe -> ShoeResponse.from(shoe, mileage.get(shoe.getId())))
                .toList();
    }

    @Transactional
    public ShoeResponse create(UUID userId, ShoeRequest request) {
        Shoe shoe = new Shoe(userId, request.name().trim());
        shoe.update(request.name().trim(), Strings.trimToNull(request.brand()), Strings.trimToNull(request.color()),
                Strings.trimToNull(request.colorSecondary()), request.purpose(), request.retirementKm(),
                request.isRetired());
        applyDefault(userId, shoe, request.wantsDefault());
        return ShoeResponse.from(shoeRepository.save(shoe), BigDecimal.ZERO);
    }

    @Transactional
    public ShoeResponse update(UUID userId, UUID shoeId, ShoeRequest request) {
        Shoe shoe = getOwned(userId, shoeId);
        shoe.update(request.name().trim(), Strings.trimToNull(request.brand()), Strings.trimToNull(request.color()),
                Strings.trimToNull(request.colorSecondary()), request.purpose(), request.retirementKm(),
                request.isRetired());
        applyDefault(userId, shoe, request.wantsDefault());
        return ShoeResponse.from(shoe, mileageByShoe(userId).get(shoeId));
    }

    /**
     * Enforces "at most one default pair per athlete": making one the default
     * clears the flag on every other pair. A retired pair can't be the default.
     */
    private void applyDefault(UUID userId, Shoe target, boolean makeDefault) {
        if (makeDefault && !target.isRetired()) {
            shoeRepository.findByUserIdOrderByRetiredAscCreatedAtDesc(userId).stream()
                    .filter(s -> !s.equals(target) && s.isDefault())
                    .forEach(Shoe::clearDefault);
            target.markDefault();
        } else {
            target.clearDefault();
        }
    }

    @Transactional
    public void delete(UUID userId, UUID shoeId) {
        shoeRepository.delete(getOwned(userId, shoeId));
    }

    /** One pair's life in numbers, computed from its linked activities. */
    @Transactional(readOnly = true)
    public ShoeStatsResponse stats(UUID userId, UUID shoeId) {
        getOwned(userId, shoeId);
        List<Activity> runs = activityRepository.findByUserIdAndShoeIdOrderByDateAsc(userId, shoeId);
        return statsOf(runs);
    }

    /**
     * Every pair with its stats in one read — the shoes page compares them
     * side by side. recentKm covers the last 90 days, for rotation share.
     */
    @Transactional(readOnly = true)
    public List<ShoeOverviewResponse> overview(UUID userId) {
        LocalDate recentFrom = LocalDate.now(AppTime.ZONE).minusDays(90);
        Map<UUID, List<Activity>> runsByShoe = activityRepository.findByUserId(userId).stream()
                .filter(a -> a.getShoeId() != null)
                .sorted(java.util.Comparator.comparing(Activity::getDate))
                .collect(Collectors.groupingBy(Activity::getShoeId));
        return shoeRepository.findByUserIdOrderByRetiredAscCreatedAtDesc(userId).stream()
                .map(shoe -> {
                    List<Activity> runs = runsByShoe.getOrDefault(shoe.getId(), List.of());
                    BigDecimal recentKm = runs.stream()
                            .filter(a -> !a.getDate().isBefore(recentFrom) && a.getDistanceKm() != null)
                            .map(Activity::getDistanceKm)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(1, RoundingMode.HALF_UP);
                    ShoeStatsResponse stats = statsOf(runs);
                    return new ShoeOverviewResponse(
                            ShoeResponse.from(shoe, stats.totalKm()), stats, recentKm);
                })
                .toList();
    }

    private static ShoeStatsResponse statsOf(List<Activity> runs) {
        BigDecimal totalKm = BigDecimal.ZERO;
        int totalElevation = 0;
        long totalMin = 0;
        for (Activity run : runs) {
            if (run.getDistanceKm() != null) {
                totalKm = totalKm.add(run.getDistanceKm());
            }
            if (run.getElevationM() != null) {
                totalElevation += run.getElevationM();
            }
            totalMin += run.getDurationMin();
        }
        Integer avgPace = totalKm.doubleValue() > 0.1
                ? (int) Math.round(totalMin * 60 / totalKm.doubleValue())
                : null;

        Map<YearMonth, BigDecimal> byMonth = runs.stream()
                .filter(run -> run.getDistanceKm() != null)
                .collect(Collectors.groupingBy(run -> YearMonth.from(run.getDate()),
                        Collectors.reducing(BigDecimal.ZERO, Activity::getDistanceKm, BigDecimal::add)));
        YearMonth current = YearMonth.from(LocalDate.now(AppTime.ZONE));
        List<ShoeStatsResponse.MonthKm> monthly = new ArrayList<>();
        for (int back = 5; back >= 0; back--) {
            YearMonth month = current.minusMonths(back);
            monthly.add(new ShoeStatsResponse.MonthKm(month.toString(),
                    byMonth.getOrDefault(month, BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP)));
        }

        return new ShoeStatsResponse(runs.size(), totalKm.setScale(1, RoundingMode.HALF_UP),
                totalElevation,
                runs.isEmpty() ? null : runs.getFirst().getDate(),
                runs.isEmpty() ? null : runs.getLast().getDate(),
                avgPace, monthly);
    }

    /**
     * Assign a pair to an activity after the fact (null clears it) — the
     * validated report and history runs both use it, so mileage stays honest.
     */
    @Transactional
    public UUID assignToActivity(UUID userId, UUID activityId, UUID shoeId) {
        Activity activity = activityRepository.findById(activityId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));
        activity.assignShoe(requireOwned(userId, shoeId));
        return activity.getShoeId();
    }

    /** Returns the shoe id only if it belongs to the athlete (null passes through). */
    @Transactional(readOnly = true)
    public UUID requireOwned(UUID userId, UUID shoeId) {
        if (shoeId == null) {
            return null;
        }
        getOwned(userId, shoeId);
        return shoeId;
    }

    private Shoe getOwned(UUID userId, UUID shoeId) {
        return shoeRepository.findById(shoeId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Shoe", shoeId));
    }

    private Map<UUID, BigDecimal> mileageByShoe(UUID userId) {
        return activityRepository.mileageByShoe(userId).stream()
                .collect(Collectors.toMap(ActivityRepository.ShoeMileage::getShoeId,
                        ActivityRepository.ShoeMileage::getTotalKm));
    }

}
