package com.cavale.integration.strava;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LapCondenserTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant START = Instant.parse("2026-09-19T04:59:24Z");

    private static StravaDtos.Lap lap(int index, long startOffset, int elapsed, int moving, double metres,
                                      Double hr, Double cadencePerLeg, Double climb) {
        return new StravaDtos.Lap(index, START.plusSeconds(startOffset), elapsed, moving, metres,
                hr, hr != null ? hr + 20 : null, cadencePerLeg, climb);
    }

    @Test
    void keepsTheWatchFiguresAndTheStartOffsetOfEachLap() {
        // The warm-up lap held a 112 s pause: elapsed 1312 s, timer 1200 s.
        JsonNode out = MAPPER.readTree(LapCondenser.toJson(List.of(
                lap(0, 0, 1312, 1200, 3304.3, 136.2, 86.0, 18.4),
                lap(1, 1313, 20, 20, 90.0, 145.0, 93.1, 0.0))));

        assertThat(out.size()).isEqualTo(2);
        JsonNode warmUp = out.get(0);
        assertThat(warmUp.get("start").asDouble()).isZero();
        assertThat(warmUp.get("elapsed").asInt()).isEqualTo(1312);
        assertThat(warmUp.get("moving").asInt()).isEqualTo(1200);
        assertThat(warmUp.get("dist").asDouble()).isEqualTo(3304.3);
        assertThat(warmUp.get("hr").asInt()).isEqualTo(136);
        assertThat(warmUp.get("maxHr").asInt()).isEqualTo(156);
        assertThat(warmUp.get("cad").asDouble()).isEqualTo(172.0);
        assertThat(warmUp.get("dplus").asInt()).isEqualTo(18);
        // The sprint starts where the timestamps say, pause included.
        assertThat(out.get(1).get("start").asDouble()).isEqualTo(1313);
    }

    @Test
    void missingSensorsBecomeNulls() {
        JsonNode out = MAPPER.readTree(LapCondenser.toJson(List.of(
                lap(0, 0, 600, 600, 1500, null, null, null),
                lap(1, 600, 600, 600, 1500, null, null, null))));

        assertThat(out.get(0).get("hr").isNull()).isTrue();
        assertThat(out.get(0).get("cad").isNull()).isTrue();
        assertThat(out.get(0).get("dplus").asInt()).isZero();
    }

    @Test
    void lapsArriveOutOfOrderAndAreSortedByIndex() {
        JsonNode out = MAPPER.readTree(LapCondenser.toJson(List.of(
                lap(1, 600, 600, 600, 1500, null, null, null),
                lap(0, 0, 600, 600, 1400, null, null, null))));

        assertThat(out.get(0).get("dist").asDouble()).isEqualTo(1400);
        assertThat(out.get(1).get("start").asDouble()).isEqualTo(600);
    }

    @Test
    void withoutTimestampsTheStartsAccumulateElapsedTime() {
        JsonNode out = MAPPER.readTree(LapCondenser.toJson(List.of(
                new StravaDtos.Lap(0, null, 1312, 1200, 3300, null, null, null, null),
                new StravaDtos.Lap(1, null, 20, 20, 90, null, null, null, null))));

        assertThat(out.get(1).get("start").asDouble()).isEqualTo(1312);
    }

    @Test
    void aSingleLapIsNothingToSplitOn() {
        assertThat(LapCondenser.toJson(List.of(lap(0, 0, 3600, 3600, 10000, null, null, null)))).isNull();
        assertThat(LapCondenser.toJson(List.of())).isNull();
        assertThat(LapCondenser.toJson(null)).isNull();
    }
}
