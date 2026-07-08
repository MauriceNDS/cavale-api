package com.cavale.training.web;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.cavale.common.config.SecurityConfig;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreatePlanRequest;
import com.cavale.training.service.PlanImportService;
import com.cavale.training.service.TrainingPlanService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainingPlanController.class)
@Import(SecurityConfig.class)
class TrainingPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainingPlanService planService;

    @MockitoBean
    private PlanImportService importService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void create_returns201WithLocation() throws Exception {
        TrainingPlan plan = new TrainingPlan(USER_ID, "SaintéLyon 2026", "sub-8h30",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        UUID planId = UUID.randomUUID();
        ReflectionTestUtils.setField(plan, "id", planId);
        when(planService.createPlan(eq(USER_ID), any(CreatePlanRequest.class))).thenReturn(plan);

        mockMvc.perform(post("/api/plans")
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "SaintéLyon 2026", "goal": "sub-8h30",
                                 "startDate": "2026-07-06", "endDate": "2026-11-29"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/plans/" + planId))
                .andExpect(jsonPath("$.name").value("SaintéLyon 2026"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Plan", "startDate": "2026-07-06", "endDate": "2026-11-29"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withInvalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/plans")
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "startDate": null, "endDate": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.startDate").exists());
    }
}
