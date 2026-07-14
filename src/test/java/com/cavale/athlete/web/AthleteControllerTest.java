package com.cavale.athlete.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cavale.athlete.dto.AthleteHubResponse;
import com.cavale.athlete.dto.AthleteHubResponse.DistanceRecord;
import com.cavale.athlete.dto.AthleteHubResponse.LongestRuns;
import com.cavale.athlete.dto.AthleteHubResponse.PeriodTotals;
import com.cavale.athlete.dto.AthleteHubResponse.Profile;
import com.cavale.athlete.dto.AthleteHubResponse.SyncState;
import com.cavale.athlete.dto.AthleteHubResponse.Totals;
import com.cavale.athlete.service.AthleteContextService;
import com.cavale.athlete.service.AthleteStatsService;
import com.cavale.common.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AthleteController.class)
@Import(SecurityConfig.class)
class AthleteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AthleteStatsService statsService;

    @MockitoBean
    private AthleteContextService contextService;

    @MockitoBean
    private com.cavale.athlete.service.ActivityFeedService feedService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void hub_returnsPayload() throws Exception {
        when(statsService.getHub(any(UUID.class))).thenReturn(new AthleteHubResponse(
                new Profile("Adel", "a@b.c", new BigDecimal("62.5"), 168,
                        LocalDate.of(1995, 3, 14), 192, 48, Instant.now()),
                List.of(),
                List.of(new DistanceRecord("10 km", 10000, 2700, LocalDate.of(2026, 3, 2), "Course")),
                new LongestRuns(null, null),
                List.of(),
                new Totals(new PeriodTotals(1, new BigDecimal("10.0"), 60, 100),
                        new PeriodTotals(2, new BigDecimal("30.0"), 150, 800)),
                List.of(),
                List.of(),
                new SyncState(true, 320, 0)));

        mockMvc.perform(get("/api/athlete/hub")
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.displayName").value("Adel"))
                .andExpect(jsonPath("$.records[0].label").value("10 km"))
                .andExpect(jsonPath("$.records[0].seconds").value(2700))
                .andExpect(jsonPath("$.sync.syncedActivities").value(320));
    }

    @Test
    void hub_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/athlete/hub"))
                .andExpect(status().isUnauthorized());
    }
}
