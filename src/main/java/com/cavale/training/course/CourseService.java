package com.cavale.training.course;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.athlete.service.RunningStatsService;
import com.cavale.athlete.service.RunningStatsService.TrailPace;
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.training.domain.Course;
import com.cavale.training.domain.CourseWaypoint;
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.WaypointKind;
import com.cavale.training.dto.CourseResponse;
import com.cavale.training.dto.CourseResponse.Split;
import com.cavale.training.dto.CourseResponse.Waypoint;
import com.cavale.training.dto.WaypointRequest;
import com.cavale.training.repository.CourseRepository;
import com.cavale.training.repository.CourseWaypointRepository;
import com.cavale.training.repository.ObjectiveRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * The GPX course planner (P12): imports a track, stores its profile, and on
 * every read computes grade-adjusted splits and aid-station arrivals from the
 * athlete's own sec/km-effort (the same trail model behind the objective
 * estimates). A single course-level ultra-fatigue factor is applied uniformly,
 * so the finish matches the objective estimate for the same distance.
 */
@Service
public class CourseService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final CourseRepository courseRepository;
    private final CourseWaypointRepository waypointRepository;
    private final ObjectiveRepository objectiveRepository;
    private final RunningStatsService runningStatsService;

    public CourseService(CourseRepository courseRepository,
                         CourseWaypointRepository waypointRepository,
                         ObjectiveRepository objectiveRepository,
                         RunningStatsService runningStatsService) {
        this.courseRepository = courseRepository;
        this.waypointRepository = waypointRepository;
        this.objectiveRepository = objectiveRepository;
        this.runningStatsService = runningStatsService;
    }

    @Transactional
    public CourseResponse importGpx(UUID userId, UUID objectiveId, String gpx, String nameOverride,
                                    LocalDate today) {
        Objective objective = ownedObjective(userId, objectiveId);
        GpxParser.ParsedCourse parsed = GpxParser.parse(gpx);
        String name = nameOverride != null && !nameOverride.isBlank() ? nameOverride.trim()
                : objective.getName();
        String profileJson = MAPPER.writeValueAsString(parsed.profile().stream()
                .map(p -> new int[]{p.distanceM(), p.elevationM()})
                .toList());
        BigDecimal distanceKm = BigDecimal.valueOf(parsed.distanceKm()).setScale(2, RoundingMode.HALF_UP);

        Course course = courseRepository.findByObjectiveId(objectiveId)
                .map(existing -> {
                    existing.replaceTrack(name, distanceKm, parsed.elevationGainM(),
                            parsed.elevationLossM(), profileJson);
                    return existing;
                })
                .orElseGet(() -> courseRepository.save(new Course(userId, objectiveId, name, distanceKm,
                        parsed.elevationGainM(), parsed.elevationLossM(), profileJson)));

        // Re-imported GPX brings its own waypoints — replace the auto ones.
        waypointRepository.deleteAll(waypointRepository.findByCourseIdOrderByDistanceKm(course.getId()));
        for (GpxParser.ParsedWaypoint w : parsed.waypoints()) {
            waypointRepository.save(new CourseWaypoint(course.getId(), w.name(), WaypointKind.AID_STATION,
                    BigDecimal.valueOf(w.distanceKm()).setScale(2, RoundingMode.HALF_UP),
                    w.elevationM(), null));
        }
        return toResponse(userId, course, today);
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourse(UUID userId, UUID objectiveId, LocalDate today) {
        return toResponse(userId, ownedCourse(userId, objectiveId), today);
    }

    @Transactional(readOnly = true)
    public boolean hasCourse(UUID objectiveId) {
        return courseRepository.findByObjectiveId(objectiveId).isPresent();
    }

    @Transactional
    public void delete(UUID userId, UUID objectiveId) {
        courseRepository.delete(ownedCourse(userId, objectiveId));
    }

    @Transactional
    public CourseResponse addWaypoint(UUID userId, UUID objectiveId, WaypointRequest request,
                                      LocalDate today) {
        Course course = ownedCourse(userId, objectiveId);
        waypointRepository.save(new CourseWaypoint(course.getId(), request.name().trim(), request.kind(),
                request.distanceKm(), request.elevationM(), trimmed(request.note())));
        return toResponse(userId, course, today);
    }

    @Transactional
    public CourseResponse deleteWaypoint(UUID userId, UUID waypointId, LocalDate today) {
        CourseWaypoint waypoint = waypointRepository.findById(waypointId)
                .orElseThrow(() -> new ResourceNotFoundException("Waypoint", waypointId));
        Course course = courseRepository.findById(waypoint.getCourseId())
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Waypoint", waypointId));
        waypointRepository.delete(waypoint);
        return toResponse(userId, course, today);
    }

    /* ── Pacing ────────────────────────────────────────────────────────── */

    private CourseResponse toResponse(UUID userId, Course course, LocalDate today) {
        List<int[]> profile = readProfile(course.getProfileJson());
        List<CourseWaypoint> waypoints = waypointRepository
                .findByCourseIdOrderByDistanceKm(course.getId());
        TrailPace pace = runningStatsService.trailPace(userId, today);

        double distanceKm = course.getDistanceKm().doubleValue();
        double totalKmEffort = distanceKm + course.getElevationGainM() / 100.0;
        double fatigue = pace != null ? pace.fatigueFor(totalKmEffort) : 1;

        List<Split> splits = splits(profile, distanceKm, pace, fatigue);
        List<Waypoint> waypointArrivals = waypoints.stream()
                .map(w -> waypoint(profile, w, pace, fatigue))
                .toList();

        return new CourseResponse(course.getId(), course.getObjectiveId(), course.getName(),
                course.getDistanceKm(), course.getElevationGainM(), course.getElevationLossM(),
                BigDecimal.valueOf(totalKmEffort).setScale(0, RoundingMode.HALF_UP),
                profile, splits, waypointArrivals,
                pace != null ? (int) Math.round(pace.medianSecPerKmEffort()) : null,
                pace != null ? pace.sampleRuns() : null,
                pace != null ? cumulativeSec(profile, pace.q1SecPerKmEffort(), distanceKm, fatigue) : null,
                pace != null ? cumulativeSec(profile, pace.medianSecPerKmEffort(), distanceKm, fatigue) : null,
                pace != null ? cumulativeSec(profile, pace.q3SecPerKmEffort(), distanceKm, fatigue) : null);
    }

    private static List<Split> splits(List<int[]> profile, double distanceKm, TrailPace pace,
                                      double fatigue) {
        double step = niceSplit(distanceKm);
        List<Split> splits = new ArrayList<>();
        double from = 0;
        int previousCumulative = 0;
        while (from < distanceKm - 0.001) {
            double to = Math.min(from + step, distanceKm);
            int climb = climbBetween(profile, from * 1000, to * 1000);
            double kmEffort = (to - from) + climb / 100.0;
            Integer cumulative = pace != null
                    ? cumulativeSec(profile, pace.medianSecPerKmEffort(), to, fatigue) : null;
            Integer segment = cumulative != null ? cumulative - previousCumulative : null;
            splits.add(new Split(round1(from), round1(to), climb, round1(kmEffort), segment, cumulative));
            if (cumulative != null) {
                previousCumulative = cumulative;
            }
            from = to;
        }
        return splits;
    }

    private static Waypoint waypoint(List<int[]> profile, CourseWaypoint w, TrailPace pace,
                                     double fatigue) {
        double km = w.getDistanceKm().doubleValue();
        int climbTo = climbBetween(profile, 0, km * 1000);
        double kmEffort = km + climbTo / 100.0;
        return new Waypoint(w.getId(), w.getName(), w.getKind(), w.getDistanceKm(), w.getElevationM(),
                w.getNote(), climbTo, round1(kmEffort),
                pace != null ? cumulativeSec(profile, pace.q1SecPerKmEffort(), km, fatigue) : null,
                pace != null ? cumulativeSec(profile, pace.medianSecPerKmEffort(), km, fatigue) : null,
                pace != null ? cumulativeSec(profile, pace.q3SecPerKmEffort(), km, fatigue) : null);
    }

    /** Seconds to reach {@code toKm}: pace × km-effort so far × the ultra-fatigue factor. */
    private static int cumulativeSec(List<int[]> profile, double paceSecPerKmEffort, double toKm,
                                     double fatigue) {
        double kmEffort = toKm + climbBetween(profile, 0, toKm * 1000) / 100.0;
        return (int) Math.round(paceSecPerKmEffort * kmEffort * fatigue);
    }

    /** Positive elevation change over the profile points within [fromM, toM]. */
    private static int climbBetween(List<int[]> profile, double fromM, double toM) {
        double climb = 0;
        Integer previousEle = null;
        for (int[] point : profile) {
            if (point[0] < fromM) {
                previousEle = point[1];
                continue;
            }
            if (point[0] > toM) {
                break;
            }
            if (previousEle != null && point[1] > previousEle) {
                climb += point[1] - previousEle;
            }
            previousEle = point[1];
        }
        return (int) Math.round(climb);
    }

    /** ~12 splits over the course, snapped to a readable interval. */
    private static double niceSplit(double distanceKm) {
        double raw = distanceKm / 12.0;
        for (double nice : new double[]{0.5, 1, 2, 2.5, 5, 10, 20}) {
            if (nice >= raw) {
                return nice;
            }
        }
        return 25;
    }

    @SuppressWarnings("unchecked")
    private static List<int[]> readProfile(String json) {
        List<List<Integer>> raw = MAPPER.readValue(json, List.class);
        return raw.stream().map(p -> new int[]{p.get(0), p.get(1)}).toList();
    }

    private Objective ownedObjective(UUID userId, UUID objectiveId) {
        return objectiveRepository.findById(objectiveId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Objective", objectiveId));
    }

    private Course ownedCourse(UUID userId, UUID objectiveId) {
        return courseRepository.findByObjectiveId(objectiveId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Course", objectiveId));
    }

    private static BigDecimal round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
