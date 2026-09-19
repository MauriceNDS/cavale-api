package com.cavale.integration.strava;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * Reduces the device laps to the handful of numbers the per-segment chart
 * needs. Each lap keeps its own summary (the watch's figures, not a re-slice
 * of the downsampled streams) plus its start offset in the activity, so the
 * segments line up with what the athlete saw on the wrist even after pauses.
 */
final class LapCondenser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private LapCondenser() {
    }

    /**
     * @return JSON [{"start":s,"elapsed":s,"moving":s,"dist":m,"hr":bpm,
     *         "maxHr":bpm,"cad":spm,"dplus":m}] or null when there is nothing
     *         to split on (a single lap is the whole activity).
     */
    static String toJson(List<StravaDtos.Lap> laps) {
        if (laps == null || laps.size() < 2) {
            return null;
        }
        List<StravaDtos.Lap> ordered = laps.stream()
                .sorted(Comparator.comparing(lap -> lap.lapIndex() != null ? lap.lapIndex() : 0))
                .toList();
        List<Map<String, Object>> out = new ArrayList<>(ordered.size());
        double cumulativeElapsed = 0;
        for (StravaDtos.Lap lap : ordered) {
            // Start offsets come from the timestamps; the running elapsed total
            // is the fallback when a lap has no start date.
            double start = lap.startDate() != null && ordered.getFirst().startDate() != null
                    ? lap.startDate().getEpochSecond() - ordered.getFirst().startDate().getEpochSecond()
                    : cumulativeElapsed;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("start", start);
            row.put("elapsed", lap.elapsedTime());
            row.put("moving", lap.movingTime());
            row.put("dist", Math.round(lap.distance() * 10.0) / 10.0);
            row.put("hr", lap.averageHeartrate() != null ? (int) Math.round(lap.averageHeartrate()) : null);
            row.put("maxHr", lap.maxHeartrate() != null ? (int) Math.round(lap.maxHeartrate()) : null);
            // Strava reports run cadence per leg; ×2 gives the usual SPM figure.
            row.put("cad", lap.averageCadence() != null
                    ? Math.round(lap.averageCadence() * 2 * 10.0) / 10.0 : null);
            row.put("dplus", lap.totalElevationGain() != null
                    ? (int) Math.round(lap.totalElevationGain()) : 0);
            out.add(row);
            cumulativeElapsed += lap.elapsedTime();
        }
        return MAPPER.writeValueAsString(out);
    }
}
