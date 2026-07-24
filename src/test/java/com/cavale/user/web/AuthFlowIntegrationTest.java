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
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack auth flow against a real Postgres: register → login → /me with
 * the issued JWT. Proves the encoder/decoder pair, the filter chain, and the
 * persistence layer work together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.cavale.user.repository.UserRepository userRepository;

    @Autowired
    private com.cavale.user.service.TokenService tokenService;

    @Test
    void credentialsClaim_letsAStravaBornAccountLogInWithEmail() throws Exception {
        // A Strava-born account: synthetic address, unusable random password.
        com.cavale.user.domain.User user = new com.cavale.user.domain.User(
                "strava-991@users.cavale.local", "$2a$10$unusable", "Strava Born");
        user.activate();
        user = userRepository.save(user);
        String token = tokenService.issueFor(user);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCredentials").value(false));

        mockMvc.perform(put("/api/users/me/credentials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "claimed@cavale.run", "password": "s3cret-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("claimed@cavale.run"))
                .andExpect(jsonPath("$.hasCredentials").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "claimed@cavale.run", "password": "s3cret-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // A second claim must be refused — the account now has real credentials.
        mockMvc.perform(put("/api/users/me/credentials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "other@cavale.run", "password": "s3cret-pass"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void fullAuthFlow_registerLoginThenMe() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "flow@cavale.run", "password": "s3cret-pass", "displayName": "Flow"}
                                """))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "flow@cavale.run", "password": "s3cret-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow@cavale.run"))
                .andExpect(jsonPath("$.displayName").value("Flow"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
