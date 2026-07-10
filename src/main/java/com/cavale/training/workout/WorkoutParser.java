package com.cavale.training.workout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cavale.training.workout.WorkoutStructure.Block;
import com.cavale.training.workout.WorkoutStructure.Node;
import com.cavale.training.workout.WorkoutStructure.Parsed;
import com.cavale.training.workout.WorkoutStructure.Section;

/**
 * Heuristic parser for the plan's Campus-format descriptions
 * ("Échauffement : … Corps : … Retour au calme : …").
 *
 * The output is strictly two-channel: the STRUCTURE carries only what a watch
 * can execute — durations, pace zones, repeat loops — while every piece of
 * prose (terrain, technique, instructions, reminders) goes to NOTES. Nothing
 * is dropped; nothing pollutes the workout body.
 */
public final class WorkoutParser {

    private WorkoutParser() {
    }

    private static final Pattern SECTION = Pattern.compile(
            "(Échauffement|Corps|Retour au calme)\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    /** e.g. "1h30", "45′", "45'", "30″", "25-30′" (a range keeps its upper bound) */
    private static final Pattern DURATION = Pattern.compile(
            "(?:(\\d+)h(\\d+)?)|(?:(?:\\d+[-–])?(\\d+)\\s*[′'](?![′'\\d]))|(?:(?:\\d+[-–])?(\\d+)\\s*[″\"])");

    /** "2 × (8 × 30″ …)" — nested repeat */
    private static final Pattern NESTED_REPEAT = Pattern.compile(
            "^(\\d+)\\s*[×x]\\s*\\(\\s*(\\d+)\\s*[×x]\\s*(.+)\\)\\s*(.*)$");

    /** "6×30/30" — classic work/recover alternation */
    private static final Pattern SLASH_REPEAT = Pattern.compile(
            "(\\d+)\\s*[×x]\\s*(\\d+)\\s*/\\s*(\\d+)");

    /** "8 × 30″ en côte…" — simple repeat */
    private static final Pattern SIMPLE_REPEAT = Pattern.compile("^(\\d+)\\s*[×x]\\s*(.+)$");

    /** "R = 3′ entre séries" — recovery attached to the previous repeat group */
    private static final Pattern SERIES_RECOVERY = Pattern.compile(
            "^R\\s*=.*|^récup(?:ération)?\\s*[:=].*", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECOVERY_SPLIT = Pattern.compile(
            "[,—-]?\\s*r[ée]cup(?:ération)?\\s*[:=]?\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern STRIDES = Pattern.compile("(\\d+)\\s*(?:[×x]\\s*)?lignes?\\b");

    private static final List<String> ZONES = List.of(
            "Seuil 60", "Seuil 30", "Récupération", "Récup", "Tempo", "VMA", "Sprint",
            "allure course", "AC", "EF", "Z1", "Z2");

    public static Parsed parse(String detail) {
        if (detail == null || detail.isBlank()) {
            return Parsed.EMPTY;
        }

        List<Block> blocks = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Matcher matcher = SECTION.matcher(detail);

        int previousEnd = 0;
        Section previousSection = null;
        while (matcher.find()) {
            if (previousSection != null) {
                addBlock(blocks, notes, previousSection, detail.substring(previousEnd, matcher.start()));
            } else if (previousEnd == 0 && !detail.substring(0, matcher.start()).isBlank()) {
                addBlock(blocks, notes, Section.MAIN, detail.substring(0, matcher.start()));
            }
            previousSection = sectionOf(matcher.group(1));
            previousEnd = matcher.end();
        }

        if (previousSection != null) {
            addBlock(blocks, notes, previousSection, detail.substring(previousEnd));
        } else {
            addBlock(blocks, notes, Section.MAIN, detail);
        }

        return new Parsed(List.copyOf(blocks), notes.isEmpty() ? null : String.join("\n", notes));
    }

    private static Section sectionOf(String label) {
        return switch (label.toLowerCase()) {
            case "échauffement" -> Section.WARMUP;
            case "retour au calme" -> Section.COOLDOWN;
            default -> Section.MAIN;
        };
    }

    private static void addBlock(List<Block> blocks, List<String> notes, Section section, String text) {
        List<Node> nodes = new ArrayList<>();
        for (String fragment : splitTopLevel(text)) {
            String label = clean(fragment);
            if (label.length() <= 2) {
                continue;
            }
            Node node = toNode(label, nodes);
            if (node != null) {
                nodes.add(node);
            }
            if (node == null || isProse(label)) {
                notes.add(label);
            }
        }
        if (!nodes.isEmpty()) {
            blocks.add(new Block(section, nodes));
        }
    }

    /** May return null: pure prose, or folded into the previous repeat group. */
    private static Node toNode(String label, List<Node> siblings) {
        // "R = 3′ entre séries" → between-series recovery, belongs INSIDE the previous loop
        if (SERIES_RECOVERY.matcher(label).matches() && !siblings.isEmpty()
                && siblings.getLast().isRepeat()) {
            Node previous = siblings.removeLast();
            List<Node> children = new ArrayList<>(previous.children());
            children.add(Node.step(label, firstDuration(label), "Récup"));
            siblings.add(Node.repeat(previous.count(), children));
            return null;
        }

        Matcher nested = NESTED_REPEAT.matcher(label);
        if (nested.matches()) {
            int outer = Integer.parseInt(nested.group(1));
            int inner = Integer.parseInt(nested.group(2));
            return Node.repeat(outer, List.of(Node.repeat(inner, workAndRecovery(nested.group(3)))));
        }

        Matcher slash = SLASH_REPEAT.matcher(label);
        if (slash.find()) {
            int count = Integer.parseInt(slash.group(1));
            String zone = zoneOf(label);
            return Node.repeat(count, List.of(
                    Node.step(label, Integer.parseInt(slash.group(2)), zone != null ? zone : "VMA"),
                    Node.step("récupération", Integer.parseInt(slash.group(3)), "Récup")));
        }

        Matcher strides = STRIDES.matcher(label);
        if (strides.find()) {
            return Node.repeat(Integer.parseInt(strides.group(1)),
                    List.of(Node.step(label, 20, "Sprint")));
        }

        Matcher simple = SIMPLE_REPEAT.matcher(label);
        if (simple.matches() && !label.toLowerCase().contains("ligne")) {
            return Node.repeat(Integer.parseInt(simple.group(1)), workAndRecovery(simple.group(2)));
        }

        Integer duration = firstDuration(label);
        String zone = zoneOf(label);
        if (duration == null && zone == null) {
            return null; // pure prose — notes only
        }
        return Node.step(label, duration, zone);
    }

    /** Splits "30″ en côte … récup = descente en trot" into work + recovery steps. */
    private static List<Node> workAndRecovery(String content) {
        String[] parts = RECOVERY_SPLIT.split(content, 2);
        String work = clean(parts[0]);
        List<Node> nodes = new ArrayList<>();
        nodes.add(Node.step(work, firstDuration(work), zoneOf(work)));
        if (parts.length > 1) {
            String recovery = clean(parts[1]);
            if (!recovery.isEmpty()) {
                nodes.add(Node.step("récup : " + recovery, firstDuration(recovery), "Récup"));
            }
        }
        return nodes;
    }

    /**
     * True when the fragment carries meaning beyond its quantified tokens —
     * terrain, technique, instructions — that must reach the notes.
     */
    private static boolean isProse(String label) {
        String residual = DURATION.matcher(label).replaceAll(" ");
        residual = residual.replaceAll("\\d+\\s*[×x/]|[()\\d]", " ");
        for (String zone : ZONES) {
            residual = residual.replace(zone, " ");
        }
        residual = residual.replaceAll(
                "(?i)\\b(r|récup(?:ération)?|entre|séries?|lignes?|droites?|à|de|en|la|le|et|=)\\b", " ");
        return residual.replaceAll("[^\\p{L}]", "").length() >= 8;
    }

    private static Integer firstDuration(String text) {
        Matcher dur = DURATION.matcher(text);
        while (dur.find()) {
            Integer seconds = toSeconds(dur);
            if (seconds != null) {
                return seconds;
            }
        }
        return null;
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

    private static String zoneOf(String label) {
        return ZONES.stream().filter(label::contains).findFirst()
                .map(z -> z.equals("Récupération") ? "Récup" : z)
                .orElse(null);
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
                if (c == 'p') i += 3; // skip "uis"
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
