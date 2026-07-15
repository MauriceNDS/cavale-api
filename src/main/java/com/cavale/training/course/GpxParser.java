package com.cavale.training.course;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Parses a GPX track into a distance/elevation profile. Distance comes from
 * the haversine between consecutive track points; cumulated climb/descent use
 * a {@value #ELE_SMOOTHING_M} m hysteresis so GPS elevation noise doesn't
 * inflate the D+. Waypoints (aid stations…) are placed at the distance of
 * their nearest track point. The profile is downsampled to
 * {@value #MAX_PROFILE_POINTS} points — plenty for a chart, tiny to store.
 */
final class GpxParser {

    private static final int MAX_PROFILE_POINTS = 300;
    private static final double ELE_SMOOTHING_M = 3.0;
    private static final double EARTH_RADIUS_M = 6_371_000;

    private GpxParser() {
    }

    /** One profile sample: cumulative distance and elevation, both in metres. */
    record ProfilePoint(int distanceM, int elevationM) {
    }

    record ParsedWaypoint(String name, double distanceKm, Integer elevationM) {
    }

    record ParsedCourse(String name, List<ProfilePoint> profile, double distanceKm,
                        int elevationGainM, int elevationLossM, List<ParsedWaypoint> waypoints) {
    }

    private record TrackPoint(double lat, double lon, double cumulativeM) {
    }

    static ParsedCourse parse(String gpx) {
        if (gpx == null || gpx.isBlank()) {
            throw new IllegalArgumentException("Empty GPX file");
        }
        Element root = readXml(gpx);
        NodeList trkpts = root.getElementsByTagName("trkpt");
        if (trkpts.getLength() < 2) {
            throw new IllegalArgumentException("The GPX file has no usable track (need at least 2 points)");
        }

        List<TrackPoint> track = new ArrayList<>(trkpts.getLength());
        List<double[]> raw = new ArrayList<>(trkpts.getLength()); // [cumM, ele]
        double cumulative = 0;
        double gain = 0;
        double loss = 0;
        Double refElevation = null;
        double lastEle = 0;
        Double prevLat = null;
        Double prevLon = null;

        for (int i = 0; i < trkpts.getLength(); i++) {
            Element point = (Element) trkpts.item(i);
            double lat = Double.parseDouble(point.getAttribute("lat"));
            double lon = Double.parseDouble(point.getAttribute("lon"));
            Double ele = childDouble(point, "ele");
            double elevation = ele != null ? ele : lastEle;
            lastEle = elevation;

            if (prevLat != null) {
                cumulative += haversine(prevLat, prevLon, lat, lon);
            }
            if (refElevation == null) {
                refElevation = elevation;
            } else if (elevation - refElevation > ELE_SMOOTHING_M) {
                gain += elevation - refElevation;
                refElevation = elevation;
            } else if (refElevation - elevation > ELE_SMOOTHING_M) {
                loss += refElevation - elevation;
                refElevation = elevation;
            }
            prevLat = lat;
            prevLon = lon;
            track.add(new TrackPoint(lat, lon, cumulative));
            raw.add(new double[]{cumulative, elevation});
        }

        String name = firstText(root, "name");
        return new ParsedCourse(name != null ? name : "Parcours",
                downsample(raw), cumulative / 1000.0,
                (int) Math.round(gain), (int) Math.round(loss),
                waypoints(root, track));
    }

    /** Reduce the raw track to a chart-friendly profile, keeping the first and last. */
    private static List<ProfilePoint> downsample(List<double[]> raw) {
        int stride = Math.max(1, raw.size() / MAX_PROFILE_POINTS);
        List<ProfilePoint> profile = new ArrayList<>();
        for (int i = 0; i < raw.size(); i += stride) {
            profile.add(new ProfilePoint((int) Math.round(raw.get(i)[0]), (int) Math.round(raw.get(i)[1])));
        }
        double[] last = raw.getLast();
        ProfilePoint lastPoint = new ProfilePoint((int) Math.round(last[0]), (int) Math.round(last[1]));
        if (!profile.getLast().equals(lastPoint)) {
            profile.add(lastPoint);
        }
        return profile;
    }

    private static List<ParsedWaypoint> waypoints(Element root, List<TrackPoint> track) {
        NodeList wpts = root.getElementsByTagName("wpt");
        List<ParsedWaypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < wpts.getLength(); i++) {
            Element wpt = (Element) wpts.item(i);
            double lat = Double.parseDouble(wpt.getAttribute("lat"));
            double lon = Double.parseDouble(wpt.getAttribute("lon"));
            String name = childText(wpt, "name");
            Double ele = childDouble(wpt, "ele");

            TrackPoint nearest = track.getFirst();
            double best = Double.MAX_VALUE;
            for (TrackPoint candidate : track) {
                double d = haversine(lat, lon, candidate.lat(), candidate.lon());
                if (d < best) {
                    best = d;
                    nearest = candidate;
                }
            }
            waypoints.add(new ParsedWaypoint(name != null ? name : "Point " + (i + 1),
                    Math.round(nearest.cumulativeM() / 10.0) / 100.0,
                    ele != null ? (int) Math.round(ele) : null));
        }
        return waypoints;
    }

    private static Element readXml(String gpx) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(gpx)))
                    .getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the GPX file: " + e.getMessage());
        }
    }

    private static Double childDouble(Element parent, String tag) {
        String text = childText(parent, tag);
        try {
            return text != null ? Double.parseDouble(text.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Direct-child text (not descendants — a wpt's own name, not a nested one). */
    private static String childText(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tag.equals(child.getNodeName())) {
                String text = child.getTextContent();
                return text != null && !text.isBlank() ? text.trim() : null;
            }
        }
        return null;
    }

    private static String firstText(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            String text = nodes.item(i).getTextContent();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
