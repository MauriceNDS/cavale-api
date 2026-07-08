package com.cavale.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cavale.user.domain.User;
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
}
