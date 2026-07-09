package com.cavale.integration.strava;

import java.time.Instant;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestStravaClient implements StravaClient {

    private final StravaProperties properties;
    private final RestClient authClient;
    private final RestClient apiClient;

    public RestStravaClient(StravaProperties properties) {
        this.properties = properties;
        this.authClient = RestClient.builder().baseUrl(properties.authBase()).build();
        this.apiClient = RestClient.builder().baseUrl(properties.apiBase()).build();
    }

    @Override
    public StravaDtos.TokenResponse exchangeCode(String code) {
        return authClient.post()
                .uri(uri -> uri.path("/oauth/token")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .queryParam("code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(StravaDtos.TokenResponse.class);
    }

    @Override
    public StravaDtos.TokenResponse refreshToken(String refreshToken) {
        return authClient.post()
                .uri(uri -> uri.path("/oauth/token")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .queryParam("refresh_token", refreshToken)
                        .queryParam("grant_type", "refresh_token")
                        .build())
                .retrieve()
                .body(StravaDtos.TokenResponse.class);
    }

    @Override
    public List<StravaDtos.ActivitySummary> listActivities(String accessToken, Instant after, Instant before) {
        return apiClient.get()
                .uri(uri -> uri.path("/athlete/activities")
                        .queryParam("after", after.getEpochSecond())
                        .queryParam("before", before.getEpochSecond())
                        .queryParam("per_page", 100)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
