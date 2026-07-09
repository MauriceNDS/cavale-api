package com.cavale.integration.strava;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;
import com.cavale.user.service.TokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StravaAuthServiceTest {

    private static final StravaProperties PROPS = new StravaProperties(
            "12345", "secret", "http://localhost:8080/api/strava/callback",
            "http://localhost:5173/settings", "http://localhost:5173/auth/strava",
            "https://www.strava.com", "https://www.strava.com/api/v3");

    @Mock
    private StravaClient stravaClient;

    @Mock
    private StravaConnectionRepository connectionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    StravaAuthServiceTest() {
        SecretKey key = new SecretKeySpec("test-secret-at-least-32-bytes-long!!".getBytes(), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private StravaAuthService service() {
        return new StravaAuthService(PROPS, stravaClient, connectionRepository, userRepository,
                passwordEncoder, tokenService, jwtEncoder, jwtDecoder);
    }

    private static StravaDtos.TokenResponse tokenResponse(long athleteId) {
        return new StravaDtos.TokenResponse("access", "refresh",
                Instant.now().plusSeconds(21600).getEpochSecond(),
                new StravaDtos.Athlete(athleteId, "Arsène", "Del Sol"));
    }

    private static String extractState(String authorizeUrl) {
        return authorizeUrl.substring(authorizeUrl.indexOf("state=") + 6);
    }

    @Test
    void login_firstTime_createsAccountAndConnectionAndReturnsToken() {
        StravaAuthService service = service();
        String state = extractState(service.loginUrl());

        when(stravaClient.exchangeCode("the-code")).thenReturn(tokenResponse(777L));
        when(connectionRepository.findByAthleteId(777L)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$random");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            return user;
        });
        when(connectionRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(tokenService.issueFor(any(User.class))).thenReturn("cavale-jwt");

        String redirect = service.handleCallback("the-code", state, null);

        assertThat(redirect).isEqualTo("http://localhost:5173/auth/strava#token=cavale-jwt");
        verify(userRepository).save(any(User.class));
        verify(connectionRepository).save(any(StravaConnection.class));
    }

    @Test
    void login_returningAthlete_reusesAccount() {
        StravaAuthService service = service();
        String state = extractState(service.loginUrl());

        UUID existingUserId = UUID.randomUUID();
        User existingUser = new User("strava-777@users.cavale.local", "$2a$x", "Arsène Del Sol");
        ReflectionTestUtils.setField(existingUser, "id", existingUserId);
        StravaConnection connection = new StravaConnection(existingUserId, 777L, "a", "r",
                Instant.now(), "activity:read_all");

        when(stravaClient.exchangeCode("the-code")).thenReturn(tokenResponse(777L));
        when(connectionRepository.findByAthleteId(777L)).thenReturn(Optional.of(connection));
        when(userRepository.findById(existingUserId)).thenReturn(Optional.of(existingUser));
        when(connectionRepository.findByUserId(existingUserId)).thenReturn(Optional.of(connection));
        when(tokenService.issueFor(existingUser)).thenReturn("cavale-jwt-2");

        String redirect = service.handleCallback("the-code", state, null);

        assertThat(redirect).endsWith("#token=cavale-jwt-2");
        verify(userRepository, never()).save(any());
    }

    @Test
    void callback_withTamperedState_redirectsToError() {
        String redirect = service().handleCallback("code", "not-a-valid-state", null);

        assertThat(redirect).isEqualTo("http://localhost:5173/settings?strava=error");
        verify(stravaClient, never()).exchangeCode(anyString());
    }

    @Test
    void callback_userDeniedConsent_redirectsToLoginError() {
        StravaAuthService service = service();
        String state = extractState(service.loginUrl());

        String redirect = service.handleCallback(null, state, "access_denied");

        assertThat(redirect).isEqualTo("http://localhost:5173/auth/strava#error");
        verify(stravaClient, never()).exchangeCode(anyString());
    }
}
