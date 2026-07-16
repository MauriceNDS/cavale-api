package com.cavale.user.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.user.config.AdminProperties;
import com.cavale.user.domain.AccountStatus;
import com.cavale.user.domain.User;
import com.cavale.user.dto.UpdateProfileRequest;
import com.cavale.user.dto.UpdateStatusRequest;
import com.cavale.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AdminProperties adminProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(normalizedEmail, passwordHash, displayName.trim());
        // Configured admins are trusted from the first sign-up: admin + active
        // right away, so a fresh install always has someone who can approve the
        // rest. Everyone else starts PENDING and waits for an admin.
        if (adminProperties.contains(normalizedEmail)) {
            user.promoteToAdmin();
            user.activate();
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        return userRepository.findByEmail(normalizedEmail)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public User updateStatus(UUID id, UpdateStatusRequest request) {
        User user = getById(id);
        String note = request.note() != null && !request.note().isBlank()
                ? request.note().trim()
                : null;
        user.updateStatus(request.status(), note, LocalDate.now());
        return user;
    }

    @Transactional
    public User updateProfile(UUID id, UpdateProfileRequest request) {
        if (request.restingHr() != null && request.maxHr() != null
                && request.restingHr() >= request.maxHr()) {
            throw new IllegalArgumentException("Resting HR must be below max HR");
        }
        User user = getById(id);
        user.updateProfile(request.displayName().trim(), request.weightKg(), request.heightCm(),
                request.birthDate(), request.maxHr(), request.restingHr());
        if (request.gymEnabled() != null) {
            user.updateGymEnabled(request.gymEnabled());
        }
        if (request.preferredLanguage() != null) {
            user.updatePreferredLanguage(request.preferredLanguage());
        }
        return user;
    }

    /* ── Admin: account access ─────────────────────────────────────────── */

    /** All accounts (newest first), optionally narrowed to one access status. */
    @Transactional(readOnly = true)
    public List<User> listUsers(AccountStatus filter) {
        return filter == null
                ? userRepository.findAllByOrderByCreatedAtDesc()
                : userRepository.findByAccountStatusOrderByCreatedAtDesc(filter);
    }

    @Transactional
    public User activate(UUID id) {
        User user = getById(id);
        user.activate();
        return user;
    }

    @Transactional
    public User deactivate(UUID id) {
        User user = getById(id);
        // An admin must never lock the console's own keepers out.
        if (user.isAdmin()) {
            throw new IllegalArgumentException("Admin accounts cannot be deactivated");
        }
        user.deactivate();
        return user;
    }
}
