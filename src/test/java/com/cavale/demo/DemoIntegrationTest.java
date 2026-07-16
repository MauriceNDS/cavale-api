package com.cavale.demo;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cavale.TestcontainersConfiguration;
import com.cavale.training.repository.ActivityRepository;
import com.cavale.training.repository.TrainingPlanRepository;
import com.cavale.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ephemeral demo sandbox end-to-end: one POST provisions a seeded, active,
 * throwaway account; PATs are refused to it; and the reaper deletes it with all
 * its data cascading away (V20). account-ttl=0 makes every demo instantly
 * reapable — the scheduled sweep still won't fire during the test (its initial
 * delay is 30 min), so cleanup only happens when we call it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "cavale.demo.account-ttl=PT0S")
class DemoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrainingPlanRepository planRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private DemoCleanupService cleanupService;

    private String startDemo(Holder tokenOut) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.demo").value(true))
                .andExpect(jsonPath("$.user.accountStatus").value("ACTIVE"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        if (tokenOut != null) {
            tokenOut.value = JsonPath.read(body, "$.token");
        }
        return JsonPath.read(body, "$.user.id");
    }

    @Test
    void startDemo_provisionsSeededActiveSandbox() throws Exception {
        Holder token = new Holder();
        UUID userId = UUID.fromString(startDemo(token));

        // The token works and reports demo mode…
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token.value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true));

        // …the account came pre-loaded with a full season…
        assertThat(planRepository.findByUserIdOrderByStartDateDesc(userId)).isNotEmpty();
        assertThat(activityRepository.findByUserId(userId)).hasSizeGreaterThanOrEqualTo(20);

        // …but it can't mint a long-lived MCP token.
        mockMvc.perform(post("/api/users/me/pat").header("Authorization", "Bearer " + token.value))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not available in demo"));
    }

    @Test
    void reaper_deletesExpiredDemoAndCascadesItsData() throws Exception {
        UUID userId = UUID.fromString(startDemo(null));
        assertThat(planRepository.findByUserIdOrderByStartDateDesc(userId)).isNotEmpty();
        assertThat(activityRepository.findByUserId(userId)).isNotEmpty();

        int reaped = cleanupService.reapExpired();

        assertThat(reaped).isGreaterThanOrEqualTo(1);
        assertThat(userRepository.findById(userId)).isEmpty();
        // The FK cascade took the whole season with it — no orphans left behind.
        assertThat(planRepository.findByUserIdOrderByStartDateDesc(userId)).isEmpty();
        assertThat(activityRepository.findByUserId(userId)).isEmpty();
    }

    private static final class Holder {
        String value;
    }
}
