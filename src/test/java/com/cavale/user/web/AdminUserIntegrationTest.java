package com.cavale.user.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account-access gate and admin console end-to-end against a real Postgres:
 * a new account is locked out until an admin activates it; the admin API is
 * itself admin-only; deactivation revokes access again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    /* ── helpers ───────────────────────────────────────────────────────── */

    private String register(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "s3cret-pass", "displayName": "%s"}
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "s3cret-pass"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private void makeAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.promoteToAdmin();
        user.activate();
        userRepository.save(user);
    }

    /* ── tests ─────────────────────────────────────────────────────────── */

    @Test
    void newAccount_canReadOwnProfileButNothingElse() throws Exception {
        register("pending@cavale.run", "Pending");
        String token = login("pending@cavale.run");

        // /me is allowed so the SPA can show the "awaiting activation" screen…
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("PENDING"))
                .andExpect(jsonPath("$.role").value("USER"));

        // …but every other protected endpoint is refused.
        mockMvc.perform(post("/api/users/me/pat").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account not active"));
    }

    @Test
    void adminApi_isForbiddenToNonAdmins() throws Exception {
        // An ACTIVE but non-admin account — access is fine, admin powers are not.
        register("regular@cavale.run", "Regular");
        User regular = userRepository.findByEmail("regular@cavale.run").orElseThrow();
        regular.activate();
        userRepository.save(regular);
        String token = login("regular@cavale.run");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void admin_listsFiltersAndActivatesAccounts() throws Exception {
        register("boss@cavale.run", "Boss");
        makeAdmin("boss@cavale.run");
        String adminToken = login("boss@cavale.run");

        String pendingId = register("newbie@cavale.run", "Newbie");

        // The pending account shows up under the PENDING filter…
        mockMvc.perform(get("/api/admin/users?status=PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'newbie@cavale.run')]").exists());

        // …and the admin activates it.
        mockMvc.perform(post("/api/admin/users/" + pendingId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        // Now the newly-activated user can reach a protected endpoint.
        String userToken = login("newbie@cavale.run");
        mockMvc.perform(post("/api/users/me/pat").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void admin_deactivatesRegularUsersButNotAdmins() throws Exception {
        register("chief@cavale.run", "Chief");
        makeAdmin("chief@cavale.run");
        String adminToken = login("chief@cavale.run");

        String victimId = register("victim@cavale.run", "Victim");
        mockMvc.perform(post("/api/admin/users/" + victimId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/" + victimId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("DISABLED"));

        // An admin cannot be locked out through the API.
        String chiefId = userRepository.findByEmail("chief@cavale.run").orElseThrow().getId().toString();
        mockMvc.perform(post("/api/admin/users/" + chiefId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}
