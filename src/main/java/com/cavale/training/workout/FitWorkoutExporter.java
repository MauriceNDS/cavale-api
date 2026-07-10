package com.cavale.training.workout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cavale.training.domain.PlannedSession;
import com.cavale.training.workout.WorkoutStructure.Block;
import com.cavale.training.workout.WorkoutStructure.Step;
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
 * Encodes a parsed workout as a Garmin .fit WORKOUT file. Steps with a known
 * duration become timed steps; repetitions become native repeat structures
 * (work + lap-press recovery); anything unquantified becomes an open step —
 * the watch guides what it can, the athlete laps the rest.
 */
@Component
public class FitWorkoutExporter {

    public byte[] export(PlannedSession session, List<Block> blocks) {
        List<WorkoutStepMesg> steps = buildSteps(blocks);

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

    private List<WorkoutStepMesg> buildSteps(List<Block> blocks) {
        List<WorkoutStepMesg> steps = new ArrayList<>();

        for (Block block : blocks) {
            Intensity intensity = switch (block.section()) {
                case WARMUP -> Intensity.WARMUP;
                case COOLDOWN -> Intensity.COOLDOWN;
                case MAIN -> Intensity.ACTIVE;
            };

            for (Step step : block.steps()) {
                if (step.repeats() != null && step.repeats() > 1 && step.durationSec() != null) {
                    int firstIndex = steps.size();
                    steps.add(timedStep(steps.size(), stepName(step), step.durationSec(), Intensity.ACTIVE));
                    steps.add(openStep(steps.size(), "Récup (lap)", Intensity.REST));
                    steps.add(repeatStep(steps.size(), firstIndex, step.repeats()));
                } else if (step.durationSec() != null) {
                    steps.add(timedStep(steps.size(), stepName(step), step.durationSec(), intensity));
                } else {
                    steps.add(openStep(steps.size(), stepName(step), intensity));
                }
            }
        }

        if (steps.isEmpty()) {
            steps.add(openStep(0, "Séance libre", Intensity.ACTIVE));
        }
        return steps;
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

    private static WorkoutStepMesg repeatStep(int index, int fromIndex, int repeats) {
        WorkoutStepMesg step = new WorkoutStepMesg();
        step.setMessageIndex(index);
        step.setDurationType(WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT);
        step.setDurationValue((long) fromIndex);
        step.setTargetType(WktStepTarget.OPEN);
        step.setTargetValue((long) repeats);
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

    private static String stepName(Step step) {
        String name = step.zone() != null ? step.zone() : step.label();
        return name.length() > 30 ? name.substring(0, 30) : name;
    }

    private static String workoutName(PlannedSession session) {
        String title = "Cavale · " + session.getTitle();
        return title.length() > 40 ? title.substring(0, 40) : title;
    }
}
