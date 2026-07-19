package com.cavale.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cavale.user.config.AdminProperties;
import com.cavale.user.domain.AccountStatus;
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
        return userService(new AdminProperties(java.util.List.of()));
    }

    private UserService userService(AdminProperties adminProperties) {
        return new UserService(userRepository, passwordEncoder, adminProperties);
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
                new java.math.BigDecimal("62.5"), 168, java.time.LocalDate.of(1995, 3, 14), 192, 48, null, null));

        assertThat(user.getDisplayName()).isEqualTo("Alice B");
        assertThat(user.getWeightKg()).isEqualByComparingTo("62.5");
        assertThat(user.getHeightCm()).isEqualTo(168);
        assertThat(user.getMaxHr()).isEqualTo(192);
        assertThat(user.getRestingHr()).isEqualTo(48);
        // untouched when the request leaves them null
        assertThat(user.isGymEnabled()).isTrue();
        assertThat(user.getPreferredLanguage()).isEqualTo("fr");
    }

    @Test
    void updateProfile_rejectsRestingHrAboveMaxHr() {
        assertThatThrownBy(() -> userService().updateProfile(java.util.UUID.randomUUID(),
                new UpdateProfileRequest("Alice", null, null, null, 180, 185, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateProfile_switchesPreferredLanguage() {
        User user = new User("alice@cavale.run", "$2a$hashed", "Alice");
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(user));

        userService().updateProfile(id, new UpdateProfileRequest("Alice",
                null, null, null, null, null, null, "en"));

        assertThat(user.getPreferredLanguage()).isEqualTo("en");
    }

    @Test
    void register_newAccountStartsPendingAndPlainUser() {
        when(userRepository.existsByEmail("bob@cavale.run")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        // someone already owns the install — Bob is not the first real account
        when(userRepository.countByDemoFalse()).thenReturn(1L);

        User saved = userService().register("bob@cavale.run", "s3cret-pass", "Bob");

        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.isAdmin()).isFalse();
    }

    @Test
    void register_veryFirstRealAccountIsAdminAndActive() {
        when(userRepository.existsByEmail("owner@cavale.run")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.countByDemoFalse()).thenReturn(0L);

        User saved = userService().register("owner@cavale.run", "s3cret-pass", "Owner");

        assertThat(saved.isAdmin()).isTrue();
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void bootstrapIfFirstAccount_promotesOnlyWhenNoRealAccountExists() {
        User first = new User("owner@cavale.run", "$2a$hashed", "Owner");
        when(userRepository.countByDemoFalse()).thenReturn(0L);
        userService().bootstrapIfFirstAccount(first);
        assertThat(first.isAdmin()).isTrue();
        assertThat(first.isActive()).isTrue();

        User second = new User("bob@cavale.run", "$2a$hashed", "Bob");
        when(userRepository.countByDemoFalse()).thenReturn(1L);
        userService().bootstrapIfFirstAccount(second);
        assertThat(second.isAdmin()).isFalse();
        assertThat(second.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void register_configuredAdminEmailIsAdminAndActiveImmediately() {
        AdminProperties admins = new AdminProperties(java.util.List.of("Owner@Cavale.RUN"));
        when(userRepository.existsByEmail("owner@cavale.run")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService(admins).register(" owner@cavale.run ", "s3cret-pass", "Owner");

        assertThat(saved.isAdmin()).isTrue();
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void activate_grantsAccess() {
        User user = new User("bob@cavale.run", "$2a$hashed", "Bob");
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(user));

        userService().activate(id);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void deactivate_revokesAccessForRegularUser() {
        User user = new User("bob@cavale.run", "$2a$hashed", "Bob");
        user.activate();
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(user));

        userService().deactivate(id);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    void deactivate_refusesToLockOutAnAdmin() {
        User admin = new User("owner@cavale.run", "$2a$hashed", "Owner");
        admin.promoteToAdmin();
        admin.activate();
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(admin));

        assertThatThrownBy(() -> userService().deactivate(id))
                .isInstanceOf(com.cavale.common.exception.ConflictException.class);
        assertThat(admin.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void listUsers_appliesStatusFilterWhenGiven() {
        userService().listUsers(AccountStatus.PENDING);
        verify(userRepository).findByAccountStatusOrderByCreatedAtDesc(AccountStatus.PENDING);

        userService().listUsers(null);
        verify(userRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void updateStatus_stampsSinceOnlyWhenStatusChanges() {
        User user = new User("alice@cavale.run", "$2a$hashed", "Alice");
        java.util.UUID id = java.util.UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(java.util.Optional.of(user));

        userService().updateStatus(id, new com.cavale.user.dto.UpdateStatusRequest(
                com.cavale.user.domain.AthleteStatus.INJURED, "  TFL genou droit  "));
        assertThat(user.getAthleteStatus()).isEqualTo(com.cavale.user.domain.AthleteStatus.INJURED);
        assertThat(user.getStatusNote()).isEqualTo("TFL genou droit");
        java.time.LocalDate firstSince = user.getStatusSince();
        assertThat(firstSince).isEqualTo(java.time.LocalDate.now());

        // same status, new note — the since-date must NOT reset
        userService().updateStatus(id, new com.cavale.user.dto.UpdateStatusRequest(
                com.cavale.user.domain.AthleteStatus.INJURED, "ça va mieux"));
        assertThat(user.getStatusSince()).isEqualTo(firstSince);
        assertThat(user.getStatusNote()).isEqualTo("ça va mieux");
    }
}
