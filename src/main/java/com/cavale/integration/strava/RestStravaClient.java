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
    public List<StravaDtos.ActivitySummary> listActivitiesPage(String accessToken, int page, int perPage) {
        return apiClient.get()
                .uri(uri -> uri.path("/athlete/activities")
                        .queryParam("page", page)
                        .queryParam("per_page", perPage)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Override
    public StravaDtos.ActivityDetail getActivity(String accessToken, long activityId) {
        return apiClient.get()
                .uri(uri -> uri.path("/activities/{id}").build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(StravaDtos.ActivityDetail.class);
    }

    @Override
    public StravaDtos.StreamSet getStreams(String accessToken, long activityId) {
        return apiClient.get()
                .uri(uri -> uri.path("/activities/{id}/streams")
                        .queryParam("keys", "time,distance,heartrate,altitude,velocity_smooth")
                        .queryParam("key_by_type", true)
                        .build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(StravaDtos.StreamSet.class);
    }

    @Override
    public StravaDtos.PushSubscription createPushSubscription(String callbackUrl, String verifyToken) {
        return apiClient.post()
                .uri(uri -> uri.path("/push_subscriptions")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .queryParam("callback_url", callbackUrl)
                        .queryParam("verify_token", verifyToken)
                        .build())
                .retrieve()
                .body(StravaDtos.PushSubscription.class);
    }

    @Override
    public List<StravaDtos.PushSubscription> listPushSubscriptions() {
        return apiClient.get()
                .uri(uri -> uri.path("/push_subscriptions")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Override
    public void deletePushSubscription(long subscriptionId) {
        apiClient.delete()
                .uri(uri -> uri.path("/push_subscriptions/{id}")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .build(subscriptionId))
                .retrieve()
                .toBodilessEntity();
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
