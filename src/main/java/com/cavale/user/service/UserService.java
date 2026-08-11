package com.cavale.user.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ConflictException;
import com.cavale.user.config.AdminProperties;
import com.cavale.user.domain.AccountStatus;
import com.cavale.user.domain.User;
import com.cavale.user.dto.UpdateCredentialsRequest;
import com.cavale.user.dto.UpdateProfileRequest;
import com.cavale.user.dto.UpdateStatusRequest;
import com.cavale.user.repository.RefreshTokenRepository;
import com.cavale.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AdminProperties adminProperties,
                       RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
        this.refreshTokenRepository = refreshTokenRepository;
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
        bootstrapIfFirstAccount(user);
        return userRepository.save(user);
    }

    /**
     * The very first real account (demo sandboxes don't count) is promoted to
     * ADMIN + ACTIVE whatever door it came through. {@code cavale.admin.emails}
     * can never match a Strava-born account — Strava's OAuth exposes no email,
     * so those get a synthetic address — and without this rule a fresh install
     * whose owner arrives via Strava has nobody able to approve accounts.
     * Call on a not-yet-saved user; idempotent with the admin-email promotion.
     */
    public void bootstrapIfFirstAccount(User user) {
        if (userRepository.countByDemoFalse() == 0) {
            user.promoteToAdmin();
            user.activate();
        }
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
        if (request.lthr() != null && request.maxHr() != null && request.lthr() >= request.maxHr()) {
            throw new IllegalArgumentException("LTHR must be below max HR");
        }
        user.updateProfile(request.displayName().trim(), request.weightKg(), request.heightCm(),
                request.birthDate(), request.maxHr(), request.restingHr(), request.lthr());
        if (request.gymEnabled() != null) {
            user.updateGymEnabled(request.gymEnabled());
        }
        if (request.preferredLanguage() != null) {
            user.updatePreferredLanguage(request.preferredLanguage());
        }
        return user;
    }

    /**
     * One-shot claim of a Strava-born account: sets a real email + password so
     * the owner can log in without Strava. Refused once real credentials exist
     * — changing an established email/password is a different (future) flow
     * with its own safeguards. No token refresh is needed afterwards: auth
     * resolves users by id, and the filter reloads the account per request;
     * the JWT's embedded email claim going stale is cosmetic.
     */
    @Transactional
    public User setCredentials(UUID id, UpdateCredentialsRequest request) {
        User user = getById(id);
        if (user.hasRealCredentials()) {
            throw new ConflictException("This account already has email/password credentials");
        }
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.endsWith(User.SYNTHETIC_EMAIL_SUFFIX)) {
            throw new ConflictException("This email domain is reserved");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }
        user.updateCredentials(normalizedEmail, passwordEncoder.encode(request.password()));
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

    /**
     * Invalidate every credential for the account (leaked-PAT kill switch).
     *
     * <p>Bumping the token version alone would not be enough: a live refresh
     * token would simply mint a new access token stamped with the NEW version
     * and sail past the gate. The refresh chain has to be cut in the same
     * breath. Uses the repository rather than RefreshTokenService, which
     * depends on this class.
     */
    @Transactional
    public void revokeTokens(UUID id) {
        getById(id).revokeTokens();
        refreshTokenRepository.revokeAllForUser(id, java.time.Instant.now());
    }

    @Transactional
    public User deactivate(UUID id) {
        User user = getById(id);
        // An admin must never lock the console's own keepers out.
        if (user.isAdmin()) {
            throw new ConflictException("Admin accounts cannot be deactivated");
        }
        user.deactivate();
        return user;
    }
}
