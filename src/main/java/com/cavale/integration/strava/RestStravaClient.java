package com.cavale.integration.strava;

import java.time.Instant;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
        MultiValueMap<String, String> form = credentialForm();
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        return authClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(StravaDtos.TokenResponse.class);
    }

    @Override
    public StravaDtos.TokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = credentialForm();
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return authClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(StravaDtos.TokenResponse.class);
    }

    /** client_id + client_secret in a form body — kept out of the URL so the
     *  application-wide secret never lands in access/proxy logs. */
    private MultiValueMap<String, String> credentialForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
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
        MultiValueMap<String, String> form = credentialForm();
        form.add("callback_url", callbackUrl);
        form.add("verify_token", verifyToken);
        return apiClient.post()
                .uri("/push_subscriptions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
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

    /** Max pages walked in one window — 200/page over a sync window is a generous
     *  ceiling that still bounds work if Strava ever returned a non-shrinking page. */
    private static final int MAX_WINDOW_PAGES = 20;
    private static final int WINDOW_PAGE_SIZE = 200;

    @Override
    public List<StravaDtos.ActivitySummary> listActivities(String accessToken, Instant after, Instant before) {
        List<StravaDtos.ActivitySummary> all = new java.util.ArrayList<>();
        for (int page = 1; page <= MAX_WINDOW_PAGES; page++) {
            final int p = page;
            List<StravaDtos.ActivitySummary> batch = apiClient.get()
                    .uri(uri -> uri.path("/athlete/activities")
                            .queryParam("after", after.getEpochSecond())
                            .queryParam("before", before.getEpochSecond())
                            .queryParam("page", p)
                            .queryParam("per_page", WINDOW_PAGE_SIZE)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (batch == null || batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < WINDOW_PAGE_SIZE) {
                break; // last page
            }
        }
        return all;
    }
}
