package com.cavale.integration.strava;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cavale.common.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StravaWebhookController.class)
@Import({SecurityConfig.class, StravaWebhookControllerTest.Config.class})
class StravaWebhookControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        StravaProperties stravaProperties() {
            return new StravaProperties("12345", "secret", "http://x/callback",
                    "http://x/settings", "http://x/login", "https://a", "https://b",
                    "https://cavale.example/api/strava/webhook", "verify-me");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StravaWebhookService webhookService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void validation_echoesChallengeForTheRightToken() throws Exception {
        mockMvc.perform(get("/api/strava/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verify-me")
                        .param("hub.challenge", "abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['hub.challenge']").value("abc123"));
    }

    @Test
    void validation_rejectsWrongToken() throws Exception {
        mockMvc.perform(get("/api/strava/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "attacker")
                        .param("hub.challenge", "abc123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void events_areAcceptedWithoutAuthAndDelegated() throws Exception {
        mockMvc.perform(post("/api/strava/webhook")
                        .contentType("application/json")
                        .content("""
                                {"object_type":"activity","object_id":9,"aspect_type":"create",
                                 "owner_id":42,"subscription_id":1,"event_time":1783900000}"""))
                .andExpect(status().isOk());

        verify(webhookService).process(any(StravaDtos.WebhookEvent.class));
    }
}
