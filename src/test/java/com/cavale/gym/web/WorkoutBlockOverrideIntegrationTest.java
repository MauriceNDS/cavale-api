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
 * Mid-workout deviations end-to-end: swap a block to its declared
 * alternative (persisted server-side — a page refresh keeps it), skip a
 * block, restore it, and keep the skip honest in the finished history.
 * The prescribed exercise derives from another one so the swap walks the
 * same lazy associations that used to 500 the reorder endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkoutBlockOverrideIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void swapSkipRestore_fullLiveWorkoutFlow() throws Exception {
        String token = registerActiveUser("workout-blocks@cavale.run");

        String backSquat = createExercise(token, "WB Back squat", null);
        String boxSquat = createExercise(token, "WB Box squat", backSquat);
        String press = createExercise(token, "WB Presse", null);
        String fentes = createExercise(token, "WB Fentes", null);

        String variantId = createTemplateAndGetVariantA(token, "WB Force");
        String te1 = addPrescription(token, variantId, boxSquat);
        String te2 = addPrescription(token, variantId, fentes);
        mockMvc.perform(post("/api/gym/template-exercises/" + te1 + "/alternatives")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\": \"%s\"}".formatted(press)))
                .andExpect(status().isCreated());

        // Start the workout from the variant — blocks come out as prescribed.
        MvcResult started = mockMvc.perform(post("/api/workouts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateVariantId\": \"%s\"}".formatted(variantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].exercise.name").value("WB Box squat"))
                .andExpect(jsonPath("$.blocks[0].swappedFrom").isEmpty())
                .andExpect(jsonPath("$.blocks[0].skipped").value(false))
                .andReturn();
        String workoutId = JsonPath.read(started.getResponse().getContentAsString(), "$.log.id");

        // The machine is taken — swap block 1 to its declared alternative.
        mockMvc.perform(put("/api/workouts/" + workoutId + "/blocks/" + te1 + "/exercise")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\": \"%s\"}".formatted(press)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercise.name").value("WB Presse"))
                .andExpect(jsonPath("$.swappedFrom.name").value("WB Box squat"))
                .andExpect(jsonPath("$.skipped").value(false));

        // An exercise that is neither prescribed nor an alternative is refused.
        mockMvc.perform(put("/api/workouts/" + workoutId + "/blocks/" + te1 + "/exercise")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\": \"%s\"}".formatted(fentes)))
                .andExpect(status().isBadRequest());

        // Sets are logged against the replacement.
        mockMvc.perform(put("/api/workouts/" + workoutId + "/sets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": "%s", "position": 0, "setNumber": 1,
                                 "reps": 8, "weightKg": 120}
                                """.formatted(press)))
                .andExpect(status().isOk());

        // No time left — skip block 2.
        mockMvc.perform(post("/api/workouts/" + workoutId + "/blocks/" + te2 + "/skip")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true));

        // The deviations survive a reload (they live server-side now).
        mockMvc.perform(get("/api/workouts/" + workoutId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].exercise.name").value("WB Presse"))
                .andExpect(jsonPath("$.blocks[0].swappedFrom.name").value("WB Box squat"))
                .andExpect(jsonPath("$.blocks[1].skipped").value(true))
                .andExpect(jsonPath("$.log.sets[0].exerciseName").value("WB Presse"));

        // Changed my mind — the block comes back.
        mockMvc.perform(post("/api/workouts/" + workoutId + "/blocks/" + te2 + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(false));

        // Skip it again and finish: history keeps the skip visible…
        mockMvc.perform(post("/api/workouts/" + workoutId + "/blocks/" + te2 + "/skip")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workouts/" + workoutId + "/finish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMin\": 40}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workouts/" + workoutId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].exercise.name").value("WB Presse"))
                .andExpect(jsonPath("$.blocks[1].skipped").value(true));

        // …and a finished workout can no longer be adjusted.
        mockMvc.perform(post("/api/workouts/" + workoutId + "/blocks/" + te2 + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    /* ── helpers ───────────────────────────────────────────────────────── */

    private String registerActiveUser(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "s3cret-pass", "displayName": "Blocks"}
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
