package com.cavale.user.web;

import jakarta.servlet.http.Cookie;

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
import com.cavale.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole point of the feature, end to end: signing in leaves a refresh
 * cookie behind, and that cookie buys a fresh access token without the athlete
 * typing a password again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private static int counter = 0;

    private String signIn() throws Exception {
        String email = "refresh-" + (++counter) + "@cavale.run";
        User user = userService.register(email, "s3cret-pass", "Refresh Tester");
        user.activate();
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"s3cret-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return cookieValue(result);
    }

    private static String cookieValue(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME);
        return cookie == null ? null : cookie.getValue();
    }

    @Test
    void signingInLeavesAnHttpOnlyRefreshCookie() throws Exception {
        String email = "cookie-shape@cavale.run";
        User user = userService.register(email, "s3cret-pass", "Cookie Shape");
        user.activate();
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"s3cret-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        // Out of JavaScript's reach, and never sent to anything but /api/auth.
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isGreaterThan(0);
    }

    @Test
    void theCookieBuysAWorkingAccessTokenAndAFreshCookie() throws Exception {
        String first = signIn();

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshCookie.NAME, first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").isNotEmpty())
                .andReturn();

        // Rotation: the cookie that comes back is a different secret…
        String second = cookieValue(refreshed);
        assertThat(second).isNotBlank().isNotEqualTo(first);

        // …and the access token it returned really opens authenticated doors.
        String token = com.jayway.jsonpath.JsonPath.read(
                refreshed.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aSpentCookieIsRefusedAndCleared() throws Exception {
        String first = signIn();
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, first)))
                .andExpect(status().isOk());

        MvcResult replayed = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshCookie.NAME, first)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // A dead cookie must not be left behind, or the client retries forever.
        assertThat(replayed.getResponse().getCookie(RefreshCookie.NAME).getMaxAge()).isZero();
    }

    @Test
    void replayingASpentCookieAlsoKillsTheSuccessorItHadAlreadyIssued() throws Exception {
        String first = signIn();
        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshCookie.NAME, first)))
                .andExpect(status().isOk())
                .andReturn();
        String second = cookieValue(rotated);

        // The thief replays the old secret…
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, first)))
                .andExpect(status().isUnauthorized());

        // …and the honest client's newer token dies with it. Both sign in again,
        // which is the only safe answer when the secret is in two places.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, second)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshingWithNoCookieAtAllIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void signingOutStopsTheCookieFromWorking() throws Exception {
        String secret = signIn();

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshCookie.NAME, secret)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, secret)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokingAnAccountsTokensAlsoStopsItRefreshing() throws Exception {
        String secret = signIn();
        String email = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(new Cookie(RefreshCookie.NAME, secret)))
                        .andReturn().getResponse().getContentAsString(),
                "$.user.email");

        User user = userRepository.findByEmail(email).orElseThrow();
        userService.revokeTokens(user.getId());

        // The kill switch would be useless if the refresh chain outlived it.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, secret)))
                .andExpect(status().isUnauthorized());
    }
}
