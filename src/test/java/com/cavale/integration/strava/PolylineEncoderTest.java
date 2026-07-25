package com.cavale.integration.strava;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolylineEncoderTest {

    @Test
    void encodesTheCanonicalGoogleExample() {
        var stream = new StravaDtos.LatLngStream(List.of(
                List.of(38.5, -120.2),
                List.of(40.7, -120.95),
                List.of(43.252, -126.453)));

        assertThat(PolylineEncoder.encode(stream)).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
    }

    @Test
    void thinsLongTracesButKeepsTheFinishPoint() {
        List<List<Double>> data = new ArrayList<>();
        for (int i = 0; i < 6000; i++) {
            data.add(List.of(45.0 + i * 1e-5, 6.0 + i * 1e-5));
        }
        String encoded = PolylineEncoder.encode(new StravaDtos.LatLngStream(data));

        assertThat(encoded).isNotNull();
        // decode point count: every point is one lat+lng pair of varint groups
        assertThat(decodeCount(encoded)).isLessThanOrEqualTo(1501).isGreaterThan(1400);
        assertThat(decodeLast(encoded)).containsExactly(45.0 + 5999 * 1e-5, 6.0 + 5999 * 1e-5);
    }

    @Test
    void returnsNullOnMissingOrDegenerateStreams() {
        assertThat(PolylineEncoder.encode(null)).isNull();
        assertThat(PolylineEncoder.encode(new StravaDtos.LatLngStream(null))).isNull();
        assertThat(PolylineEncoder.encode(new StravaDtos.LatLngStream(List.of(List.of(45.0, 6.0))))).isNull();
    }

    /* ── tiny reference decoder, test-only ─────────────────────────────── */

    private static int decodeCount(String encoded) {
        return decode(encoded).size();
    }

    private static List<Double> decodeLast(String encoded) {
        List<double[]> points = decode(encoded);
        double[] last = points.get(points.size() - 1);
        return List.of(last[0], last[1]);
    }

    private static List<double[]> decode(String encoded) {
        List<double[]> points = new ArrayList<>();
        long lat = 0;
        long lng = 0;
        int i = 0;
        while (i < encoded.length()) {
            long[] r = decodeValue(encoded, i);
            lat += r[0];
            i = (int) r[1];
            r = decodeValue(encoded, i);
            lng += r[0];
            i = (int) r[1];
            points.add(new double[] {lat / 1e5, lng / 1e5});
        }
        return points;
    }

    private static long[] decodeValue(String encoded, int index) {
        long result = 0;
        int shift = 0;
        long b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        long value = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
        return new long[] {value, index};
    }
}
