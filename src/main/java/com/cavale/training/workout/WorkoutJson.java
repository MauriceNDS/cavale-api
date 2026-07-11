package com.cavale.training.workout;

import java.util.List;

import com.cavale.training.workout.WorkoutStructure.Node;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** (De)serializes the stored workout tree. */
public final class WorkoutJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<Node>> NODE_LIST = new TypeReference<>() {
    };

    private WorkoutJson() {
    }

    public static String write(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(nodes);
    }

    public static List<Node> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return MAPPER.readValue(json, NODE_LIST);
    }
}
