package com.cavale.training.workout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cavale.training.workout.WorkoutStructure.Block;
import com.cavale.training.workout.WorkoutStructure.Section;
import com.cavale.training.workout.WorkoutStructure.Step;

/**
 * Heuristic parser for the plan's Campus-format descriptions
 * ("Échauffement : … Corps : … Retour au calme : …") into workout blocks.
 * Best-effort by design: anything it can't quantify stays readable in the
 * step label — never dropped.
 */
public final class WorkoutParser {

    private WorkoutParser() {
    }

    private static final Pattern SECTION = Pattern.compile(
            "(Échauffement|Corps|Retour au calme)\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    /** e.g. "1h30", "45′", "45'", "30″", "25-30′" (a range keeps its upper bound) */
    private static final Pattern DURATION = Pattern.compile(
            "(?:(\\d+)h(\\d+)?)|(?:(?:\\d+[-–])?(\\d+)\\s*[′'](?![′'\\d]))|(?:(?:\\d+[-–])?(\\d+)\\s*[″\"])");

    /** e.g. "2 × (8 × 30″ …)" or "6 × 80–100 m" */
    private static final Pattern REPEATS = Pattern.compile(
            "(\\d+)\\s*[×x]\\s*(?:\\(\\s*(\\d+)\\s*[×x])?");

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
                // text before the first section marker → treat as main content
                addBlock(blocks, Section.MAIN, detail.substring(0, matcher.start()));
            }
            previousSection = sectionOf(matcher.group(1));
            previousEnd = matcher.end();
        }

        if (previousSection != null) {
            addBlock(blocks, previousSection, detail.substring(previousEnd));
        } else {
            // no Campus markers at all → the whole text is one main block
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
        List<Step> steps = parseSteps(text);
        if (!steps.isEmpty()) {
            blocks.add(new Block(section, steps));
        }
    }

    private static List<Step> parseSteps(String text) {
        List<Step> steps = new ArrayList<>();
        // split into fragments on "+", ";", "puis" — outside parentheses
        for (String fragment : splitTopLevel(text)) {
            String label = clean(fragment);
            if (label.isEmpty() || label.length() <= 2) {
                continue;
            }
            steps.add(toStep(label));
        }
        return steps;
    }

    private static Step toStep(String label) {
        Integer repeats = null;
        String repeatLabel = null;
        Matcher rep = REPEATS.matcher(label);
        if (rep.find()) {
            int outer = Integer.parseInt(rep.group(1));
            if (rep.group(2) != null) {
                int inner = Integer.parseInt(rep.group(2));
                repeats = outer * inner;
                repeatLabel = outer + " × " + inner;
            } else {
                repeats = outer;
                repeatLabel = String.valueOf(outer);
            }
        }

        Integer durationSec = null;
        Matcher dur = DURATION.matcher(label);
        // with repeats, the duration of one repetition is the LAST time token
        // inside the repeated expression; without, the first token wins
        while (dur.find()) {
            Integer value = toSeconds(dur);
            if (value != null) {
                durationSec = value;
                if (repeats == null) {
                    break;
                }
            }
        }

        String zone = ZONES.stream().filter(label::contains).findFirst()
                .map(z -> z.equals("Récupération") ? "Récup" : z)
                .orElse(null);

        // "6 lignes droites" / "3 lignes" → short strides
        if (zone == null && label.toLowerCase().contains("ligne")) {
            zone = "Sprint";
            if (durationSec == null) {
                durationSec = 20;
            }
            if (repeats == null) {
                Matcher strides = Pattern.compile("(\\d+)\\s+lignes?").matcher(label);
                if (strides.find()) {
                    repeats = Integer.parseInt(strides.group(1));
                    repeatLabel = strides.group(1);
                }
            }
        }

        return new Step(label, repeats, repeatLabel, durationSec, zone);
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
