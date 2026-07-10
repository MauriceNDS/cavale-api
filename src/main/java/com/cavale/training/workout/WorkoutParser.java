package com.cavale.training.workout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cavale.training.workout.WorkoutStructure.Block;
import com.cavale.training.workout.WorkoutStructure.Node;
import com.cavale.training.workout.WorkoutStructure.Section;

/**
 * Heuristic parser for the plan's Campus-format descriptions
 * ("Échauffement : … Corps : … Retour au calme : …") into a workout tree:
 * steps and (possibly nested) repeat groups. Best-effort by design — anything
 * it can't quantify stays readable in a step label, never dropped.
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
            "^(\\d+)\\s*[×x]\\s*\\(\\s*(\\d+)\\s*[×x]\\s*(.+)\\)\\s*$");

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

    public static List<Block> parse(String detail) {
        if (detail == null || detail.isBlank()) {
            return List.of();
        }

        List<Block> blocks = new ArrayList<>();
        Matcher matcher = SECTION.matcher(detail);

        int previousEnd = 0;
        Section previousSection = null;
        while (matcher.find()) {
            if (previousSection != null) {
                addBlock(blocks, previousSection, detail.substring(previousEnd, matcher.start()));
            } else if (previousEnd == 0 && !detail.substring(0, matcher.start()).isBlank()) {
                addBlock(blocks, Section.MAIN, detail.substring(0, matcher.start()));
            }
            previousSection = sectionOf(matcher.group(1));
            previousEnd = matcher.end();
        }

        if (previousSection != null) {
            addBlock(blocks, previousSection, detail.substring(previousEnd));
        } else {
            addBlock(blocks, Section.MAIN, detail);
        }
        return List.copyOf(blocks);
    }

    private static Section sectionOf(String label) {
        return switch (label.toLowerCase()) {
            case "échauffement" -> Section.WARMUP;
            case "retour au calme" -> Section.COOLDOWN;
            default -> Section.MAIN;
        };
    }

    private static void addBlock(List<Block> blocks, Section section, String text) {
        List<Node> nodes = parseNodes(text);
        if (!nodes.isEmpty()) {
            blocks.add(new Block(section, nodes));
        }
    }

    private static List<Node> parseNodes(String text) {
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
        }
        return nodes;
    }

    /** May return null when the fragment was folded into the previous repeat group. */
    private static Node toNode(String label, List<Node> siblings) {
        // "R = 3′ entre séries" → between-series recovery, belongs INSIDE the previous loop
        if (SERIES_RECOVERY.matcher(label).matches() && !siblings.isEmpty()
                && siblings.getLast().isRepeat()) {
            Node previous = siblings.removeLast();
            List<Node> children = new ArrayList<>(previous.children());
            children.add(Node.step(label, firstDuration(label), null));
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
            int workSec = Integer.parseInt(slash.group(2));
            int recoverSec = Integer.parseInt(slash.group(3));
            String zone = zoneOf(label);
            return Node.repeat(count, List.of(
                    Node.step(label, workSec, zone != null ? zone : "VMA"),
                    Node.step("récupération", recoverSec, "Récup")));
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

        return Node.step(label, firstDuration(label), zoneOf(label));
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
