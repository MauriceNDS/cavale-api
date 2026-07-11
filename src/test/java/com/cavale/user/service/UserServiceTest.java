package com.cavale.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cavale.user.domain.User;
import com.cavale.user.dto.UpdateProfileRequest;
import com.cavale.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService() {
        return new UserService(userRepository, passwordEncoder);
    }

    @Test
    void register_normalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmail("alice@cavale.run")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService().register("  Alice@Cavale.RUN ", "s3cret-pass", " Alice ");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@cavale.run");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Alice");
        assertThat(saved).isSameAs(captor.getValue());
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@cavale.run")).thenReturn(true);

        assertThatThrownBy(() -> userService().register("alice@cavale.run", "s3cret-pass", "Alice"))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .hasMessageContaining("alice@cavale.run");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void authenticate_returnsUserOnMatchingPassword() {
        User user = new User("alice@cavale.run", "$2a$hashed", "Alice");
        when(userRepository.findByEmail("alice@cavale.run")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("s3cret-pass", "$2a$hashed")).thenReturn(true);

        User authenticated = userService().authenticate(" Alice@Cavale.RUN ", "s3cret-pass");

        assertThat(authenticated).isSameAs(user);
    }

    @Test
    void authenticate_rejectsWrongPassword() {
        User user = new User("alice@cavale.run", "$2a$hashed", "Alice");
        when(userRepository.findByEmail("alice@cavale.run")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService().authenticate("alice@cavale.run", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void authenticate_rejectsUnknownEmail() {
        when(userRepository.findByEmail("ghost@cavale.run")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> userService().authenticate("ghost@cavale.run", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void updateProfile_replacesAthleteData() {
        User user = new User("alice@cavale.run", "$2a$hashed", "Alice");
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(user));

        userService().updateProfile(id, new UpdateProfileRequest("  Alice B ",
                new java.math.BigDecimal("62.5"), 168, java.time.LocalDate.of(1995, 3, 14), 192, 48));

        assertThat(user.getDisplayName()).isEqualTo("Alice B");
        assertThat(user.getWeightKg()).isEqualByComparingTo("62.5");
        assertThat(user.getHeightCm()).isEqualTo(168);
        assertThat(user.getMaxHr()).isEqualTo(192);
        assertThat(user.getRestingHr()).isEqualTo(48);
    }

    @Test
    void updateProfile_rejectsRestingHrAboveMaxHr() {
        assertThatThrownBy(() -> userService().updateProfile(java.util.UUID.randomUUID(),
                new UpdateProfileRequest("Alice", null, null, null, 180, 185)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
