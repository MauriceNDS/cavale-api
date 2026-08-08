package com.cavale.integration.strava;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * Reduces raw Strava streams (~1 point/second) to ≤ MAX_POINTS evenly spaced
 * samples — plenty for charts, tiny in the database.
 */
final class StreamDownsampler {

    private static final int MAX_POINTS = 300;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private StreamDownsampler() {
    }

    /**
     * @return JSON {"time":[s],"mtime":[s],"distance":[m],"hr":[bpm],"alt":[m],
     *         "vel":[m/s],"cad":[spm]} or null.
     */
    static String toJson(StravaDtos.StreamSet streams) {
        if (streams == null || streams.time() == null || streams.time().data() == null
                || streams.time().data().size() < 2) {
            return null;
        }
        int size = streams.time().data().size();
        int stride = Math.max(1, size / MAX_POINTS);

        Map<String, List<Double>> out = Map.of(
                "time", sample(streams.time().data(), size, stride),
                // Cumulative MOVING seconds, integrated at full resolution before
                // downsampling — a pause between two kept samples would otherwise
                // be invisible, and pace per km would read minutes too slow.
                "mtime", sample(movingElapsed(streams), size, stride),
                "distance", sample(streams.distance() != null ? streams.distance().data() : null, size, stride),
                "hr", sample(streams.heartrate() != null ? streams.heartrate().data() : null, size, stride),
                "alt", sample(streams.altitude() != null ? streams.altitude().data() : null, size, stride),
                "vel", sample(streams.velocitySmooth() != null ? streams.velocitySmooth().data() : null, size, stride),
                // Strava reports run cadence per leg; ×2 gives the usual SPM figure.
                "cad", sample(streams.cadence() != null ? streams.cadence().data() : null, size, stride, 2.0));
        return MAPPER.writeValueAsString(out);
    }

    /**
     * Running total of the seconds Strava considered the athlete to be moving.
     * A sample's flag governs the interval that ends at it. Null when the
     * activity carries no usable {@code moving} stream — consumers then fall
     * back to their own stationary heuristic.
     */
    private static List<Double> movingElapsed(StravaDtos.StreamSet streams) {
        List<Double> time = streams.time().data();
        if (streams.moving() == null || streams.moving().data() == null
                || streams.moving().data().size() != time.size()) {
            return null;
        }
        List<Boolean> moving = streams.moving().data();
        List<Double> cumulative = new ArrayList<>(time.size());
        double total = 0;
        cumulative.add(0.0);
        for (int i = 1; i < time.size(); i++) {
            Double now = time.get(i);
            Double before = time.get(i - 1);
            if (now != null && before != null && now > before && !Boolean.FALSE.equals(moving.get(i))) {
                total += now - before;
            }
            cumulative.add(total);
        }
        return cumulative;
    }

    private static List<Double> sample(List<Double> data, int expectedSize, int stride) {
        return sample(data, expectedSize, stride, 1.0);
    }

    private static List<Double> sample(List<Double> data, int expectedSize, int stride, double factor) {
        if (data == null || data.size() != expectedSize) {
            return List.of();
        }
        List<Double> sampled = new ArrayList<>();
        for (int i = 0; i < data.size(); i += stride) {
            Double v = data.get(i);
            sampled.add(v == null ? null : Math.round(v * factor * 100.0) / 100.0);
        }
        return sampled;
    }
}
