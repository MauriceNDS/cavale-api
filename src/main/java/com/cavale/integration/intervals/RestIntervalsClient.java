package com.cavale.integration.intervals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestIntervalsClient implements IntervalsClient {

    private final RestClient apiClient;

    public RestIntervalsClient(IntervalsProperties properties) {
        this.apiClient = RestClient.builder().baseUrl(properties.apiBase()).build();
    }

    @Override
    public IntervalsDtos.Athlete getAthlete(String apiKey) {
        return apiClient.get()
                .uri("/athlete/0")
                .header("Authorization", basicAuth(apiKey))
                .retrieve()
                .body(IntervalsDtos.Athlete.class);
    }

    @Override
    public List<IntervalsDtos.EventResult> upsertEvents(String apiKey,
                                                        List<IntervalsDtos.EventPayload> events) {
        return apiClient.post()
                .uri("/athlete/0/events/bulk?upsert=true")
                .header("Authorization", basicAuth(apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(events)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    /** Intervals.icu API-key auth is HTTP Basic with the literal user "API_KEY". */
    private static String basicAuth(String apiKey) {
        String credentials = "API_KEY:" + apiKey;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
