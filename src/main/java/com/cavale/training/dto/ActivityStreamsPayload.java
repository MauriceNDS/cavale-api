package com.cavale.training.dto;

import com.cavale.training.domain.Activity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the chart endpoints serve: the downsampled streams object as stored,
 * with the device laps grafted in as a {@code laps} array when the activity
 * has them. Both are stored as opaque JSON text, so the merge is the one
 * place they meet.
 */
public final class ActivityStreamsPayload {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ActivityStreamsPayload() {
    }

    /** @return the JSON to serve, or null when the activity has no streams. */
    public static String of(Activity activity) {
        String streams = activity.getStreamsJson();
        if (streams == null) {
            return null;
        }
        if (activity.getLapsJson() == null) {
            return streams;
        }
        JsonNode root = MAPPER.readTree(streams);
        if (!(root instanceof ObjectNode object)) {
            return streams;
        }
        object.set("laps", MAPPER.readTree(activity.getLapsJson()));
        return MAPPER.writeValueAsString(object);
    }
}
