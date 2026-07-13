package com.cavale.user.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.user.domain.User;
import com.cavale.user.dto.UpdateProfileRequest;
import com.cavale.user.dto.UpdateStatusRequest;
import com.cavale.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        return userRepository.save(new User(normalizedEmail, passwordHash, displayName.trim()));
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
        return user;
    }
}
