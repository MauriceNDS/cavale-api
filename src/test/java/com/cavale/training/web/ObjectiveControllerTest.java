package com.cavale.training.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import com.cavale.training.domain.Objective;
import com.cavale.training.domain.ObjectiveRole;
import com.cavale.training.domain.ObjectiveType;
import com.cavale.training.domain.TrainingPlan;
import com.cavale.training.dto.CreateObjectiveRequest;
import com.cavale.training.service.ObjectiveService;
import com.cavale.training.service.PlanProgressService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ObjectiveController.class)
@Import(SecurityConfig.class)
class ObjectiveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObjectiveService objectiveService;

    @MockitoBean
    private PlanProgressService progressService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private static Objective mainObjective() {
        TrainingPlan plan = new TrainingPlan(USER_ID, "SaintéLyon 2026", "SaintéLyon 80 km",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 11, 29));
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        Objective objective = new Objective(plan, ObjectiveRole.MAIN, ObjectiveType.RACE,
                "SaintéLyon 80 km", LocalDate.of(2026, 11, 29));
        objective.updateRaceProfile(new BigDecimal("78.00"), 2100, "Saint-Étienne → Lyon");
        objective.updateTargetTimeMin(720);
        ReflectionTestUtils.setField(objective, "id", UUID.randomUUID());
        return objective;
    }

    @Test
    void list_returnsObjectives() throws Exception {
        when(objectiveService.listForPlan(USER_ID, PLAN_ID)).thenReturn(List.of(mainObjective()));

        mockMvc.perform(get("/api/plans/{planId}/objectives", PLAN_ID)
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("MAIN"))
                .andExpect(jsonPath("$[0].name").value("SaintéLyon 80 km"))
                .andExpect(jsonPath("$[0].targetTimeMin").value(720));
    }

    @Test
    void create_returns201() throws Exception {
        Objective secondary = mainObjective();
        ReflectionTestUtils.setField(secondary, "role", ObjectiveRole.SECONDARY);
        when(objectiveService.addSecondary(eq(USER_ID), eq(PLAN_ID), any(CreateObjectiveRequest.class)))
                .thenReturn(secondary);

        mockMvc.perform(post("/api/plans/{planId}/objectives", PLAN_ID)
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "RACE", "name": "Trail des Coursières", "date": "2026-09-20"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SECONDARY"));
    }

    @Test
    void create_withInvalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/plans/{planId}/objectives", PLAN_ID)
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": null, "name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void delete_mainObjective_returns409() throws Exception {
        UUID objectiveId = UUID.randomUUID();
        doThrow(new com.cavale.common.exception.ConflictException("A plan keeps its main objective"))
                .when(objectiveService).delete(USER_ID, objectiveId);

        mockMvc.perform(delete("/api/objectives/{id}", objectiveId)
                        .with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isConflict());
    }

    @Test
    void progress_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/plans/{planId}/progress", PLAN_ID))
                .andExpect(status().isUnauthorized());
    }
}
