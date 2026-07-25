package com.cavale.user.web;

import java.util.List;

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
import com.cavale.user.service.TokenService;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full lifecycle of an individually-revocable personal access token: issue
 * with a label, authenticate with it, list it, revoke it, and prove the
 * revocation bites immediately while the session survives. Legacy PATs
 * (no jti) keep working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PersonalTokenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private User activeUser(String email) {
        User user = new User(email, "$2a$10$unusable", "Pat Owner");
        user.activate();
        return userRepository.save(user);
    }

    @Test
    void patLifecycle_issueUseListRevoke() throws Exception {
        User user = activeUser("pat-owner@cavale.run");
        String session = tokenService.issueFor(user);

        MvcResult issued = mockMvc.perform(post("/api/users/me/pat")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "Claude on the laptop"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Claude on the laptop"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String pat = JsonPath.read(issued.getResponse().getContentAsString(), "$.token");
        String tokenId = JsonPath.read(issued.getResponse().getContentAsString(), "$.id");

        // The PAT authenticates API calls…
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + pat))
                .andExpect(status().isOk());

        // …and shows up in the list, un-revoked.
        mockMvc.perform(get("/api/users/me/pats").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tokenId))
                .andExpect(jsonPath("$[0].label").value("Claude on the laptop"))
                .andExpect(jsonPath("$[0].revoked").value(false));

        mockMvc.perform(delete("/api/users/me/pats/" + tokenId)
                        .header("Authorization", "Bearer " + session))
                .andExpect(status().isNoContent());

        // Revocation bites immediately for the PAT, but not for the session.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + pat))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Token revoked"));
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me/pats").header("Authorization", "Bearer " + session))
                .andExpect(jsonPath("$[0].revoked").value(true));
    }

    @Test
    void deadTokens_areCappedAtTenOldestDeleted() throws Exception {
        User user = activeUser("hoarder@cavale.run");
        String session = tokenService.issueFor(user);

        // Issue and immediately revoke 12 tokens — two more than the cap.
        for (int i = 0; i < 12; i++) {
            MvcResult issued = mockMvc.perform(post("/api/users/me/pat")
                            .header("Authorization", "Bearer " + session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\": \"t%d\"}".formatted(i)))
                    .andExpect(status().isOk())
                    .andReturn();
            String tokenId = JsonPath.read(issued.getResponse().getContentAsString(), "$.id");
            mockMvc.perform(delete("/api/users/me/pats/" + tokenId)
                            .header("Authorization", "Bearer " + session))
                    .andExpect(status().isNoContent());
        }

        // Only the 10 newest dead rows survive; t0 and t1 were pruned.
        MvcResult listed = mockMvc.perform(get("/api/users/me/pats")
                        .header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andReturn();
        List<String> labels = JsonPath.read(listed.getResponse().getContentAsString(), "$[*].label");
        org.assertj.core.api.Assertions.assertThat(labels)
                .hasSize(10)
                .containsExactly("t11", "t10", "t9", "t8", "t7", "t6", "t5", "t4", "t3", "t2");
    }

    @Test
    void legacyPatWithoutJti_staysHonoured() throws Exception {
        User user = activeUser("legacy-pat@cavale.run");
        String legacyPat = tokenService.issuePersonalToken(user, null).token();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + legacyPat))
                .andExpect(status().isOk());
    }

    @Test
    void revoke_refusesAnotherUsersToken() throws Exception {
        User owner = activeUser("owner@cavale.run");
        User thief = activeUser("thief@cavale.run");
        String ownerSession = tokenService.issueFor(owner);
        String thiefSession = tokenService.issueFor(thief);

        MvcResult issued = mockMvc.perform(post("/api/users/me/pat")
                        .header("Authorization", "Bearer " + ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "Owner's token"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String tokenId = JsonPath.read(issued.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/users/me/pats/" + tokenId)
                        .header("Authorization", "Bearer " + thiefSession))
                .andExpect(status().isNotFound());
    }
}
