package com.cavale.training.course;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpxParserTest {

    @Test
    void parsesDistanceElevationProfileAndWaypoints() {
        // three points ~786 m apart at latitude 45°, climbing then descending
        String gpx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk><name>Test Trail</name><trkseg>
                    <trkpt lat="45.0" lon="5.00"><ele>100</ele></trkpt>
                    <trkpt lat="45.0" lon="5.01"><ele>150</ele></trkpt>
                    <trkpt lat="45.0" lon="5.02"><ele>120</ele></trkpt>
                  </trkseg></trk>
                  <wpt lat="45.0" lon="5.01"><ele>150</ele><name>Ravito 1</name></wpt>
                </gpx>""";

        GpxParser.ParsedCourse course = GpxParser.parse(gpx);

        assertThat(course.name()).isEqualTo("Test Trail");
        assertThat(course.distanceKm()).isBetween(1.5, 1.65); // ~1.57 km
        assertThat(course.elevationGainM()).isEqualTo(50);     // 100 → 150
        assertThat(course.elevationLossM()).isEqualTo(30);     // 150 → 120
        assertThat(course.profile()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(course.profile().getLast().elevationM()).isEqualTo(120);

        assertThat(course.waypoints()).hasSize(1);
        GpxParser.ParsedWaypoint aid = course.waypoints().getFirst();
        assertThat(aid.name()).isEqualTo("Ravito 1");
        assertThat(aid.distanceKm()).isBetween(0.7, 0.85); // sits on the middle point
    }

    @Test
    void smallElevationWigglesDoNotInflateTheClimb() {
        // ±1 m GPS noise between two flat points must not accumulate as D+
        String gpx = """
                <gpx version="1.1">
                  <trk><trkseg>
                    <trkpt lat="45.0" lon="5.00"><ele>200</ele></trkpt>
                    <trkpt lat="45.0" lon="5.001"><ele>201</ele></trkpt>
                    <trkpt lat="45.0" lon="5.002"><ele>200</ele></trkpt>
                    <trkpt lat="45.0" lon="5.003"><ele>201</ele></trkpt>
                  </trkseg></trk>
                </gpx>""";

        GpxParser.ParsedCourse course = GpxParser.parse(gpx);

        assertThat(course.elevationGainM()).isZero();
        assertThat(course.elevationLossM()).isZero();
    }

    @Test
    void rejectsGpxWithoutAUsableTrack() {
        assertThatThrownBy(() -> GpxParser.parse("<gpx></gpx>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GpxParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
