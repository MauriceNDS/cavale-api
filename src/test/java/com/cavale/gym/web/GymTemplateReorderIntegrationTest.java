package com.cavale.gym.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cavale.TestcontainersConfiguration;
import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reordering a variant's exercises through the real HTTP stack, against a real
 * Postgres and with OSIV off. Regression test: the controller used to map the
 * reordered prescriptions to DTOs OUTSIDE the service transaction, and each
 * prescription's exercise is a lazy proxy — first touch blew up with a
 * LazyInitializationException → 500 (the exercise below derives from another
 * one to walk the exact association the production stack trace died on).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GymTemplateReorderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void reorder_returns200WithTheNewOrderAssembledInsideTheTransaction() throws Exception {
        String token = registerActiveUser("reorder@cavale.run");

        String backSquat = createExercise(token, "Back squat", null);
        String boxSquat = createExercise(token, "Box squat", backSquat);
        String fentes = createExercise(token, "Fentes marchées", null);

        String variantId = createTemplateAndGetVariantA(token, "Force hivernale");
        String firstTe = addPrescription(token, variantId, boxSquat);
        String secondTe = addPrescription(token, variantId, fentes);
        mockMvc.perform(post("/api/gym/template-exercises/" + firstTe + "/alternatives")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\": \"%s\"}".formatted(backSquat)))
                .andExpect(status().isCreated());

        // Swap the two prescriptions — used to 500 on the lazy exercise proxy.
        mockMvc.perform(put("/api/gym/variants/" + variantId + "/exercises/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\": [\"%s\", \"%s\"]}".formatted(secondTe, firstTe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[0].exercise.name").value("Fentes marchées"))
                .andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[1].exercise.name").value("Box squat"))
                .andExpect(jsonPath("$[1].exercise.derivedFromName").value("Back squat"))
                .andExpect(jsonPath("$[1].alternatives[0].exercise.name").value("Back squat"));

        // The new order survived the commit.
        mockMvc.perform(get("/api/gym/variants/" + variantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].exercise.name").value("Fentes marchées"))
                .andExpect(jsonPath("$.exercises[1].exercise.name").value("Box squat"));
    }

    /* ── helpers ───────────────────────────────────────────────────────── */

    private String registerActiveUser(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "s3cret-pass", "displayName": "Reorder"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        user.activate();
        userRepository.save(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "s3cret-pass"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String createExercise(String token, String name, String derivedFromId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/exercises")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "category": "FORCE", "equipment": "BARBELL",
                                 "measure": "WEIGHT_REPS", "muscles": ["QUADRICEPS"],
                                 "derivedFromId": %s}
                                """.formatted(name,
                                derivedFromId == null ? "null" : "\"" + derivedFromId + "\"")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTemplateAndGetVariantA(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/gym/templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.variants[0].id");
    }

    private String addPrescription(String token, String variantId, String exerciseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/gym/variants/" + variantId + "/exercises")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": "%s", "sets": 3, "reps": 6, "restSec": 180}
                                """.formatted(exerciseId)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
