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

    /** @return JSON {"time":[s],"distance":[m],"hr":[bpm],"alt":[m],"vel":[m/s]} or null. */
    static String toJson(StravaDtos.StreamSet streams) {
        if (streams == null || streams.time() == null || streams.time().data() == null
                || streams.time().data().size() < 2) {
            return null;
        }
        int size = streams.time().data().size();
        int stride = Math.max(1, size / MAX_POINTS);

        Map<String, List<Double>> out = Map.of(
                "time", sample(streams.time() != null ? streams.time().data() : null, size, stride),
                "distance", sample(streams.distance() != null ? streams.distance().data() : null, size, stride),
                "hr", sample(streams.heartrate() != null ? streams.heartrate().data() : null, size, stride),
                "alt", sample(streams.altitude() != null ? streams.altitude().data() : null, size, stride),
                "vel", sample(streams.velocitySmooth() != null ? streams.velocitySmooth().data() : null, size, stride));
        return MAPPER.writeValueAsString(out);
    }

    private static List<Double> sample(List<Double> data, int expectedSize, int stride) {
        if (data == null || data.size() != expectedSize) {
            return List.of();
        }
        List<Double> sampled = new ArrayList<>();
        for (int i = 0; i < data.size(); i += stride) {
            Double v = data.get(i);
            sampled.add(v == null ? null : Math.round(v * 100.0) / 100.0);
        }
        return sampled;
    }
}
