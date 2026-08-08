package com.cavale.user.web;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.user.config.DevLoginProperties;
import com.cavale.user.domain.User;
import com.cavale.user.dto.AuthResponse;
import com.cavale.user.repository.UserRepository;
import com.cavale.user.service.RefreshTokenService;
import com.cavale.user.service.InvalidCredentialsException;
import com.cavale.user.service.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevLoginControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private RefreshCookie refreshCookie;

    private static User user() {
        User user = new User("dev@cavale.run", "hash", "Dev");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void doorClosedBehavesLikeBadCredentials() {
        DevLoginController controller = new DevLoginController(
                new DevLoginProperties(false), userRepository, tokenService, refreshTokenService, refreshCookie);

        assertThatThrownBy(() -> controller.login(new DevLoginController.DevLoginRequest("dev@cavale.run"), new MockHttpServletResponse()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void doorOpenExchangesAKnownEmailForASession() {
        User user = user();
        when(userRepository.findByEmail("dev@cavale.run")).thenReturn(Optional.of(user));
        when(tokenService.issueFor(user)).thenReturn("jwt");
        DevLoginController controller = new DevLoginController(
                new DevLoginProperties(true), userRepository, tokenService, refreshTokenService, refreshCookie);

        AuthResponse response = controller.login(
                new DevLoginController.DevLoginRequest("  Dev@Cavale.run "),
                new MockHttpServletResponse());

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.user().email()).isEqualTo("dev@cavale.run");
    }

    @Test
    void unknownEmailIsRejectedEvenWithTheDoorOpen() {
        when(userRepository.findByEmail("ghost@cavale.run")).thenReturn(Optional.empty());
        DevLoginController controller = new DevLoginController(
                new DevLoginProperties(true), userRepository, tokenService, refreshTokenService, refreshCookie);

        assertThatThrownBy(() -> controller.login(new DevLoginController.DevLoginRequest("ghost@cavale.run"), new MockHttpServletResponse()))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
