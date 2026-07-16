package com.cavale.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.user.repository.UserRepository;

/**
 * Promotes the configured admin emails to ADMIN + ACTIVE once the app is up —
 * covering accounts that already existed before this feature (or before the
 * email was added to the config). Accounts that register later are promoted at
 * registration time by {@code UserService}. Idempotent: only the app log
 * records a change.
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserRepository userRepository;

    public AdminBootstrap(AdminProperties properties, UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteConfiguredAdmins() {
        for (String email : properties.emails()) {
            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                boolean changed = false;
                if (!user.isAdmin()) {
                    user.promoteToAdmin();
                    changed = true;
                }
                if (!user.isActive()) {
                    user.activate();
                    changed = true;
                }
                if (changed) {
                    log.info("Promoted {} to ADMIN + ACTIVE", email);
                }
            }, () -> log.info("Admin email {} is not registered yet — it will be promoted on sign-up", email));
        }
    }
}
