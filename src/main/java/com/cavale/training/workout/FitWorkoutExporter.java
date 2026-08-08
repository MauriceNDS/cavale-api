package com.cavale.training.workout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cavale.training.domain.PlannedSession;
import com.cavale.training.workout.WorkoutStructure.Allure;
import com.cavale.training.workout.WorkoutStructure.Node;
import com.garmin.fit.DateTime;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.Intensity;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.Sport;
import com.garmin.fit.WktStepDuration;
import com.garmin.fit.WktStepTarget;
import com.garmin.fit.WorkoutMesg;
import com.garmin.fit.WorkoutStepMesg;

/**
 * Encodes the canonical workout tree as a Garmin .fit WORKOUT file. Every
 * block is a timed step named by its allure and, inside a série, by its rep
 * number; récupérations carry REST intensity.
 *
 * <p>Loops are unrolled rather than encoded as native FIT repeats: a repeat
 * block carries a single name for all its reps, so mid-session the watch
 * cannot tell you whether this is the fifth or the seventh. See
 * {@link WorkoutFlattener}.
 */
@Component
public class FitWorkoutExporter {

    /** The FIT profile's cap on wkt_step_name. */
    private static final int MAX_STEP_NAME = 30;

    private static final Map<Allure, String> ALLURE_NAME = Map.of(
            Allure.LENTE, "Récup",
            Allure.EF, "Allure EF",
            Allure.COURSE, "Allure Course",
            Allure.SEUIL60, "Allure Seuil 60",
            Allure.SEUIL30, "Allure Seuil 30",
            Allure.VMA, "Allure VMA",
            Allure.SPRINT, "Allure Sprint");

    public byte[] export(PlannedSession session, List<Node> nodes) {
        List<WorkoutStepMesg> steps = new ArrayList<>();
        appendNodes(steps, nodes);
        if (steps.isEmpty()) {
            steps.add(openStep(0, "Séance libre", Intensity.ACTIVE));
        }

        WorkoutMesg workout = new WorkoutMesg();
        workout.setWktName(workoutName(session));
        workout.setSport(Sport.RUNNING);
        workout.setNumValidSteps(steps.size());

        FileIdMesg fileId = new FileIdMesg();
        fileId.setType(com.garmin.fit.File.WORKOUT);
        fileId.setManufacturer(Manufacturer.DEVELOPMENT);
        fileId.setProduct(1);
        fileId.setSerialNumber(session.getId().getMostSignificantBits() & 0xFFFFL);
        fileId.setTimeCreated(new DateTime(new Date()));

        try {
            File tmp = Files.createTempFile("cavale-workout", ".fit").toFile();
            try {
                FileEncoder encoder = new FileEncoder(tmp, Fit.ProtocolVersion.V1_0);
                encoder.write(fileId);
                encoder.write(workout);
                steps.forEach(encoder::write);
                encoder.close();
                return Files.readAllBytes(tmp.toPath());
            } finally {
                Files.deleteIfExists(tmp.toPath());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the .fit workout", e);
        }
    }

    private void appendNodes(List<WorkoutStepMesg> steps, List<Node> nodes) {
        for (WorkoutFlattener.FlatStep flat : WorkoutFlattener.flatten(nodes)) {
            Node node = flat.node();
            Intensity intensity = node.allure() == Allure.LENTE ? Intensity.REST : Intensity.ACTIVE;
            String name = stepName(node, flat.repLabel());
            if (node.seconds() != null) {
                steps.add(timedStep(steps.size(), name, node.seconds(), intensity));
            } else {
                steps.add(openStep(steps.size(), name, intensity));
            }
        }
    }

    /** FIT caps a step name at 30 characters. The rep number is the part the
     *  athlete reads mid-interval, so the allure gives way to it, never the
     *  other way round. */
    private static String stepName(Node node, String repLabel) {
        String name = ALLURE_NAME.getOrDefault(node.allure(), "Étape");
        if (node.terrain() != null) {
            name += switch (node.terrain()) {
                case COTE -> " (côte)";
                case DESCENTE -> " (descente)";
                case PLAT -> "";
            };
        }
        if (repLabel == null) {
            return name.length() > MAX_STEP_NAME ? name.substring(0, MAX_STEP_NAME) : name;
        }
        String suffix = " " + repLabel;
        int room = MAX_STEP_NAME - suffix.length();
        if (room <= 0) {
            return repLabel.substring(0, Math.min(repLabel.length(), MAX_STEP_NAME));
        }
        return (name.length() > room ? name.substring(0, room) : name) + suffix;
    }

    private static WorkoutStepMesg timedStep(int index, String name, int durationSec, Intensity intensity) {
        WorkoutStepMesg step = base(index, name, intensity);
        step.setDurationType(WktStepDuration.TIME);
        step.setDurationValue(durationSec * 1000L); // milliseconds
        return step;
    }

    private static WorkoutStepMesg openStep(int index, String name, Intensity intensity) {
        WorkoutStepMesg step = base(index, name, intensity);
        step.setDurationType(WktStepDuration.OPEN);
        return step;
    }

    private static WorkoutStepMesg base(int index, String name, Intensity intensity) {
        WorkoutStepMesg step = new WorkoutStepMesg();
        step.setMessageIndex(index);
        step.setWktStepName(name);
        step.setIntensity(intensity);
        step.setTargetType(WktStepTarget.OPEN);
        return step;
    }

    private static String workoutName(PlannedSession session) {
        String title = "Cavale · " + session.getTitle();
        return title.length() > 40 ? title.substring(0, 40) : title;
    }
}
