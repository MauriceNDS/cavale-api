package com.cavale.integration.strava;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes a GPS trace as a Google encoded polyline (precision 1e-5), the
 * compact standard every map library decodes natively. The trace is first
 * thinned to ≤ MAX_POINTS evenly spaced samples — denser than the chart
 * streams, small enough to store inline (~6 bytes/point).
 */
final class PolylineEncoder {

    private static final int MAX_POINTS = 1500;

    private PolylineEncoder() {
    }

    /** @return encoded polyline, or null when the stream is missing/degenerate. */
    static String encode(StravaDtos.LatLngStream latlng) {
        if (latlng == null || latlng.data() == null || latlng.data().size() < 2) {
            return null;
        }
        List<List<Double>> points = thin(latlng.data());
        StringBuilder out = new StringBuilder(points.size() * 6);
        long previousLat = 0;
        long previousLng = 0;
        for (List<Double> point : points) {
            if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
                continue;
            }
            long lat = Math.round(point.get(0) * 1e5);
            long lng = Math.round(point.get(1) * 1e5);
            encodeValue(lat - previousLat, out);
            encodeValue(lng - previousLng, out);
            previousLat = lat;
            previousLng = lng;
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static List<List<Double>> thin(List<List<Double>> data) {
        int stride = Math.max(1, data.size() / MAX_POINTS);
        if (stride == 1) {
            return data;
        }
        List<List<Double>> thinned = new ArrayList<>();
        for (int i = 0; i < data.size(); i += stride) {
            thinned.add(data.get(i));
        }
        // keep the exact finish point — a lopped-off last segment looks broken
        if (!thinned.get(thinned.size() - 1).equals(data.get(data.size() - 1))) {
            thinned.add(data.get(data.size() - 1));
        }
        return thinned;
    }

    private static void encodeValue(long value, StringBuilder out) {
        long v = value < 0 ? ~(value << 1) : value << 1;
        while (v >= 0x20) {
            out.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        out.append((char) (v + 63));
    }
}
