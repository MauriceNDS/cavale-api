package com.cavale.demo;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;

/**
 * Provisions ephemeral demo accounts. Each call mints a fresh, isolated,
 * fully-seeded sandbox: active immediately (bypassing the activation gate),
 * reachable only through its short-lived token (its password is random and
 * never revealed), and reaped after the configured TTL.
 */
@Service
public class DemoService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoSeeder demoSeeder;
    private final DemoCleanupService cleanupService;
    private final DemoProperties properties;

    public DemoService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       DemoSeeder demoSeeder, DemoCleanupService cleanupService,
                       DemoProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoSeeder = demoSeeder;
        this.cleanupService = cleanupService;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    @Transactional
    public User provision(LocalDate today) {
        if (!properties.enabled()) {
            throw new DemoUnavailableException("The live demo is currently disabled.");
        }
        // At capacity: try to make room by reaping anything already expired.
        if (userRepository.countByDemoTrue() >= properties.maxLive()) {
            cleanupService.reapExpired();
            if (userRepository.countByDemoTrue() >= properties.maxLive()) {
                throw new DemoUnavailableException(
                        "The demo is at capacity right now — please try again in a few minutes.");
            }
        }

        String email = "demo-" + UUID.randomUUID().toString().substring(0, 12) + "@demo.cavale.run";
        // Random, never-revealed password: the demo token is the only way in.
        User user = new User(email, passwordEncoder.encode(UUID.randomUUID().toString()), "Athlète démo");
        user.activate();
        user.markDemo();
        user.updateGymEnabled(false); // running-focused showcase
        // The seeded season is written in French — keep the UI language matched.
        user.updatePreferredLanguage("fr");
        user = userRepository.save(user);

        demoSeeder.seed(user.getId(), today);
        return user;
    }
}
