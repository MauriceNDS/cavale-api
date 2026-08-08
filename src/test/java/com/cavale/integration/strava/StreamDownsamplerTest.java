package com.cavale.integration.strava;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class StreamDownsamplerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode parse(StravaDtos.StreamSet streams) {
        return MAPPER.readTree(StreamDownsampler.toJson(streams));
    }

    /** A 1 Hz recording of {@code seconds} samples, one metre per second. */
    private static StravaDtos.StreamSet oneHertz(int seconds, List<Boolean> moving) {
        List<Double> time = new ArrayList<>();
        List<Double> distance = new ArrayList<>();
        for (int i = 0; i < seconds; i++) {
            time.add((double) i);
            distance.add((double) i);
        }
        return new StravaDtos.StreamSet(new StravaDtos.Stream(time), new StravaDtos.Stream(distance),
                null, null, null, null,
                moving == null ? null : new StravaDtos.BooleanStream(moving), null);
    }

    /** Every sample is moving except the {@code stopLength} ones from {@code stopAt}. */
    private static List<Boolean> stoppedFor(int samples, int stopAt, int stopLength) {
        List<Boolean> moving = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            moving.add(i < stopAt || i >= stopAt + stopLength);
        }
        return moving;
    }

    private static double last(JsonNode array) {
        return array.get(array.size() - 1).asDouble();
    }

    @Test
    void movingTimeLeavesOutTheSecondsSpentStandingStill() {
        JsonNode out = parse(oneHertz(100, stoppedFor(100, 40, 30)));

        JsonNode mtime = out.get("mtime");
        assertThat(mtime.size()).isEqualTo(out.get("time").size());
        assertThat(mtime.get(0).asDouble()).isZero();
        // The kept samples span `last(time)` seconds, 30 of which were stopped.
        assertThat(last(mtime)).isEqualTo(last(out.get("time")) - 30);
    }

    @Test
    void aPauseFallingBetweenTwoKeptSamplesStillCounts() {
        // 3000 samples stride down to ~300, so a 30 s stop lands entirely
        // between two kept points. Integrating before sampling is what saves it.
        JsonNode out = parse(oneHertz(3000, stoppedFor(3000, 1000, 30)));

        assertThat(out.get("time").size()).isLessThanOrEqualTo(310);
        assertThat(last(out.get("mtime"))).isEqualTo(last(out.get("time")) - 30);
    }

    @Test
    void movingTimeMatchesElapsedWhenNothingWasPaused() {
        JsonNode out = parse(oneHertz(500, stoppedFor(500, 0, 0)));

        assertThat(last(out.get("mtime"))).isEqualTo(last(out.get("time")));
    }

    @Test
    void movingTimeIsEmptyWithoutTheMovingStream() {
        JsonNode out = parse(oneHertz(100, null));

        assertThat(out.get("mtime")).isEmpty();
        assertThat(out.get("time")).isNotEmpty();
    }

    @Test
    void movingTimeIsEmptyWhenTheStreamsDisagreeOnLength() {
        JsonNode out = parse(oneHertz(100, List.of(true, true, true)));

        assertThat(out.get("mtime")).isEmpty();
    }
}
