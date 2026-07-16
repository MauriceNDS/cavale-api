package com.cavale.demo;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;

/**
 * Reaps expired demo accounts. Deleting a demo {@code User} row cascades to all
 * of its data (V20 made every user-owned FK {@code ON DELETE CASCADE}), so the
 * database never accumulates demo clutter.
 */
@Service
public class DemoCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DemoCleanupService.class);

    private final UserRepository userRepository;
    private final DemoProperties properties;

    public DemoCleanupService(UserRepository userRepository, DemoProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${cavale.demo.reap-interval:PT30M}",
            initialDelayString = "${cavale.demo.reap-interval:PT30M}")
    @Transactional
    public int reapExpired() {
        Instant cutoff = Instant.now().minus(properties.accountTtl());
        List<User> expired = userRepository.findByDemoTrueAndCreatedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }
        userRepository.deleteAll(expired);
        log.info("Reaped {} expired demo account(s)", expired.size());
        return expired.size();
    }
}
