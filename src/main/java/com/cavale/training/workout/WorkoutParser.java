package com.cavale.training.workout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;
import com.cavale.training.workout.WorkoutStructure.Parsed;
import com.cavale.training.workout.WorkoutStructure.Terrain;

/**
 * IMPORT tool: converts the plan's Campus-format text into the canonical
 * workout model (allure blocks, loops, deterministic durations). Used at
 * import/backfill time — the app then reads only the STORED structure, which
 * the athlete can edit with the builder.
 *
 * Deterministic guarantees over best-effort text:
 * - warm-ups without an allure become EF; cool-downs become LENTE;
 * - every loop has at least two blocks (a récupération is added if missing,
 *   defaulting to 1 min LENTE);
 * - when the text yields no structure at all, the session's own zone +
 *   duration synthesize a single block — never an empty body;
 * - all prose goes to notes (consignes), nothing is dropped.
 */
public final class WorkoutParser {

    private WorkoutParser() {
    }

    private static final int DEFAULT_RECOVERY_SEC = 60;
    private static final int STRIDE_SEC = 20;

    private static final Pattern SECTION = Pattern.compile(
            "(Échauffement|Corps|Retour au calme)\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern DURATION = Pattern.compile(
            "(?:(\\d+)h(\\d+)?)|(?:(?:\\d+[-–])?(\\d+)\\s*(?:[′']|min\\b)(?![′'\\d]))|(?:(?:\\d+[-–])?(\\d+)\\s*(?:[″\"]|sec\\b))");

    private static final Pattern NESTED_REPEAT = Pattern.compile(
            "^(\\d+)\\s*[×x]\\s*\\(\\s*(\\d+)\\s*[×x]\\s*(.+)\\)\\s*(.*)$");

    private static final Pattern SLASH_REPEAT = Pattern.compile(
            "(\\d+)\\s*[×x]\\s*(\\d+)\\s*/\\s*(\\d+)");

    private static final Pattern SIMPLE_REPEAT = Pattern.compile("^(\\d+)\\s*[×x]\\s*(.+)$");

    private static final Pattern SERIES_RECOVERY = Pattern.compile(
            "^R\\s*=.*|^récup(?:ération)?\\s*[:=].*", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECOVERY_SPLIT = Pattern.compile(
            "[,—-]?\\s*r[ée]cup(?:ération)?\\s*[:=]?\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern STRIDES = Pattern.compile("(\\d+)\\s*(?:[×x]\\s*)?lignes?\\b");

    /**
     * Strength/core work named inside running text. The parser drops it from
     * the structure (it is its own session, not a block of the run), and
     * {@code validate_plan} flags the session so the coach splits it out
     * instead of leaving it buried in a run's description.
     */
    public static final Pattern STRENGTH_WORK = Pattern.compile(
            "\\b(gainage|renfo(?:rcement)?|réathlétisation|ppg)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * @param detail            the imported description text (may be null)
     * @param fallbackZone      the session's zone label, used when the text has no structure
     * @param fallbackDurationMin the session's planned duration, same purpose
     */
    public static Parsed parse(String detail, String fallbackZone, Integer fallbackDurationMin) {
        List<Node> nodes = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        if (detail != null && !detail.isBlank()) {
            Matcher matcher = SECTION.matcher(detail);
            int previousEnd = 0;
            String previousSection = null;
            while (matcher.find()) {
                if (previousSection != null) {
                    parseInto(nodes, notes, previousSection, detail.substring(previousEnd, matcher.start()));
                } else if (previousEnd == 0 && !detail.substring(0, matcher.start()).isBlank()) {
                    parseInto(nodes, notes, "corps", detail.substring(0, matcher.start()));
                }
                previousSection = matcher.group(1).toLowerCase();
                previousEnd = matcher.end();
            }
            if (previousSection != null) {
                parseInto(nodes, notes, previousSection, detail.substring(previousEnd));
            } else {
                parseInto(nodes, notes, "corps", detail);
            }
        }

        // Never an empty body: synthesize from the session's own zone + duration
        if (nodes.isEmpty() && fallbackDurationMin != null) {
            nodes.add(Node.step(allureOfZone(fallbackZone), fallbackDurationMin * 60, null));
        }

        // A repeat whose only child is a repeat collapses (2×(8×…) with no
        // between-series recovery → 16×…): loops always hold ≥ 2 blocks
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.isRepeat() && node.children().size() == 1 && node.children().getFirst().isRepeat()) {
                Node inner = node.children().getFirst();
                nodes.set(i, Node.repeat(node.count() * inner.count(), inner.children()));
            }
        }

        // The main run often lives in the title ("SL trail 2h45 … fin à allure
        // course"): when the parsed structure covers far less than the session
        // duration, the missing time becomes a leading EF block
        if (fallbackDurationMin != null && !nodes.isEmpty()) {
            int structureSec = totalSeconds(nodes);
            int gap = fallbackDurationMin * 60 - structureSec;
            if (gap >= 20 * 60) {
                nodes.add(0, Node.step(Allure.EF, gap, null));
            }
        }

        return new Parsed(List.copyOf(nodes), notes.isEmpty() ? null : String.join("\n", notes));
    }

    private static void parseInto(List<Node> nodes, List<String> notes, String section, String text) {
        Allure sectionDefault = switch (section) {
            case "échauffement" -> Allure.EF;
            case "retour au calme" -> Allure.LENTE;
            default -> null;
        };

        for (String fragment : splitTopLevel(text)) {
            String label = clean(fragment);
            if (label.length() <= 2) {
                continue;
            }
            Node node = toNode(label, nodes, sectionDefault);
            if (node != null) {
                nodes.add(node);
            }
            if (node == null || isProse(label)) {
                notes.add(label);
            }
        }
    }

    /** May return null: pure prose, or folded into the previous repeat group. */
    private static Node toNode(String label, List<Node> siblings, Allure sectionDefault) {
        // strength/core work never belongs in a running structure (it's its own session)
        if (STRENGTH_WORK.matcher(label).find()) {
            return null;
        }
        if (SERIES_RECOVERY.matcher(label).matches() && !siblings.isEmpty()
                && siblings.getLast().isRepeat()) {
            Node previous = siblings.removeLast();
            List<Node> children = new ArrayList<>(previous.children());
            Integer seconds = firstDuration(label);
            Node recovery = Node.step(Allure.LENTE, seconds != null ? seconds : DEFAULT_RECOVERY_SEC, null);
            // an explicit recovery REPLACES the default one, never stacks on it
            if (children.size() >= 2 && !children.getLast().isRepeat()
                    && children.getLast().allure() == Allure.LENTE) {
                children.set(children.size() - 1, recovery);
            } else {
                children.add(recovery);
            }
            siblings.add(Node.repeat(previous.count(), children));
            return null;
        }

        Matcher nested = NESTED_REPEAT.matcher(label);
        if (nested.matches()) {
            int outer = Integer.parseInt(nested.group(1));
            int inner = Integer.parseInt(nested.group(2));
            String innerContent = nested.group(3);
            // "2 × (8 × 40″/20″)" — the slash sets the exact recovery time
            Matcher innerSlash = Pattern.compile("^(\\d+)\\s*[″\"]?\\s*/\\s*(\\d+)").matcher(innerContent.trim());
            List<Node> innerNodes = innerSlash.find()
                    ? List.of(Node.step(allureOf(innerContent, Allure.VMA),
                            Integer.parseInt(innerSlash.group(1)), terrainOf(innerContent)),
                            Node.step(Allure.LENTE, Integer.parseInt(innerSlash.group(2)), null))
                    : workAndRecovery(innerContent);
            return Node.repeat(outer, List.of(Node.repeat(inner, innerNodes)));
        }

        Matcher slash = SLASH_REPEAT.matcher(label);
        if (slash.find()) {
            Allure allure = allureOf(label, Allure.VMA);
            return Node.repeat(Integer.parseInt(slash.group(1)), List.of(
                    Node.step(allure, Integer.parseInt(slash.group(2)), terrainOf(label)),
                    Node.step(Allure.LENTE, Integer.parseInt(slash.group(3)), null)));
        }

        Matcher strides = STRIDES.matcher(label);
        if (strides.find()) {
            return Node.repeat(Integer.parseInt(strides.group(1)), List.of(
                    Node.step(Allure.SPRINT, STRIDE_SEC, null),
                    Node.step(Allure.LENTE, DEFAULT_RECOVERY_SEC, null)));
        }

        Matcher simple = SIMPLE_REPEAT.matcher(label);
        if (simple.matches() && !label.toLowerCase().contains("ligne")) {
            return Node.repeat(Integer.parseInt(simple.group(1)), workAndRecovery(simple.group(2)));
        }

        Integer duration = firstDuration(label);
        if (duration == null) {
            return null; // a block without a time is prose — notes only
        }
        Allure allure = allureOf(label, null);
        if (allure == null) {
            allure = sectionDefault != null ? sectionDefault : Allure.EF;
        }
        return Node.step(allure, duration, terrainOf(label));
    }

    /** "30″ en côte … récup = descente en trot" → work block + récupération block. */
    private static List<Node> workAndRecovery(String content) {
        String[] parts = RECOVERY_SPLIT.split(content, 2);
        String work = clean(parts[0]);
        Integer workSec = firstDuration(work);

        List<Node> nodes = new ArrayList<>();
        // metre-based strides ("80–100 m en accélération progressive") = short sprints
        boolean metreStrides = workSec == null
                && work.matches(".*\\d+\\s*(?:[-–]\\s*\\d+\\s*)?m\\b.*")
                && work.toLowerCase().matches(".*(progressiv|accélération|vitesse).*");
        if (metreStrides) {
            nodes.add(Node.step(Allure.SPRINT, STRIDE_SEC, terrainOf(work)));
        } else {
            nodes.add(Node.step(allureOf(work, Allure.VMA), workSec, terrainOf(work)));
        }

        Integer recoverySec = parts.length > 1 ? firstDuration(parts[1]) : null;
        // a loop always has its récupération — deterministic default when unstated
        nodes.add(Node.step(Allure.LENTE,
                recoverySec != null ? recoverySec
                        : workSec != null ? Math.max(DEFAULT_RECOVERY_SEC, workSec) : DEFAULT_RECOVERY_SEC,
                null));
        return nodes;
    }

    private static boolean isProse(String label) {
        String residual = DURATION.matcher(label).replaceAll(" ");
        residual = residual.replaceAll("\\d+\\s*[×x/]|[()\\d]", " ");
        residual = residual.replaceAll(
                "(?i)\\b(r|récup(?:ération)?|entre|séries?|lignes?|droites?|à|de|en|la|le|et|=|min|sec|EF|VMA|AC|Seuil|Tempo|Sprint|côte|descente|allure|course|Z1|Z2)\\b",
                " ");
        return residual.replaceAll("[^\\p{L}]", "").length() >= 8;
    }

    /** Blocks longer than this are clock times ("départ 19h30"), not durations. */
    private static final int MAX_BLOCK_SEC = 6 * 3600;

    private static Integer firstDuration(String text) {
        Matcher dur = DURATION.matcher(text);
        while (dur.find()) {
            Integer seconds = toSeconds(dur);
            if (seconds != null && seconds <= MAX_BLOCK_SEC) {
                return seconds;
            }
        }
        return null;
    }

    private static int totalSeconds(List<Node> nodes) {
        int total = 0;
        for (Node node : nodes) {
            if (node.isRepeat()) {
                total += node.count() * totalSeconds(node.children());
            } else if (node.seconds() != null) {
                total += node.seconds();
            }
        }
        return total;
    }

    private static Integer toSeconds(Matcher dur) {
        if (dur.group(1) != null) {
            return Integer.parseInt(dur.group(1)) * 3600
                    + (dur.group(2) != null ? Integer.parseInt(dur.group(2)) * 60 : 0);
        }
        if (dur.group(3) != null) {
            return Integer.parseInt(dur.group(3)) * 60;
        }
        if (dur.group(4) != null) {
            return Integer.parseInt(dur.group(4));
        }
        return null;
    }

    private static Allure allureOf(String label, Allure fallback) {
        String lower = label.toLowerCase();
        if (lower.contains("seuil 60")) return Allure.SEUIL60;
        if (lower.contains("seuil 30")) return Allure.SEUIL30;
        if (lower.contains("intensité maximale")) return Allure.SEUIL30; // LTHR test effort
        if (lower.contains("vma")) return Allure.VMA;
        if (lower.contains("sprint") || lower.contains("ligne")) return Allure.SPRINT;
        if (lower.contains("allure course") || lower.contains("tempo") || label.contains("AC")) return Allure.COURSE;
        // EF wins over incidental mentions of walking ("marcher si la respiration s'emballe")
        if (label.contains("EF") || lower.contains("endurance fondamentale") || label.contains("Z2")) return Allure.EF;
        if (lower.contains("récup") || lower.contains("très facile") || lower.contains("marche")
                || lower.contains("trot") || label.contains("Z1")) return Allure.LENTE;
        return fallback;
    }

    /** Maps a stored session zone label to the canonical allure. */
    public static Allure allureOfZone(String zone) {
        if (zone == null) return Allure.EF;
        Allure detected = allureOf(zone, null);
        if (detected != null) return detected;
        if (zone.contains("Test")) return Allure.SEUIL30;
        return Allure.EF;
    }

    private static Terrain terrainOf(String label) {
        String lower = label.toLowerCase();
        if (lower.contains("côte")) return Terrain.COTE;
        if (lower.contains("descen")) return Terrain.DESCENTE;
        return null;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> fragments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth = Math.max(0, depth - 1);
            boolean splitHere = depth == 0
                    && (c == '+' || c == ';'
                    || (c == 'p' && text.startsWith("puis ", i) && i > 0 && Character.isWhitespace(text.charAt(i - 1))));
            if (splitHere) {
                fragments.add(current.toString());
                current.setLength(0);
                if (c == 'p') i += 3;
            } else {
                current.append(c);
            }
        }
        fragments.add(current.toString());
        return fragments;
    }

    private static String clean(String fragment) {
        return fragment
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s,.:;—-]+|[\\s,.:;—-]+$", "")
                .trim();
    }
}
