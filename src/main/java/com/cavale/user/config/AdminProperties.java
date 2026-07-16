package com.cavale.user.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Emails promoted to ADMIN + ACTIVE — at registration and on startup.
 * Configured via {@code cavale.admin.emails} (env {@code CAVALE_ADMIN_EMAILS},
 * comma-separated). Idempotent, so it is safe to leave configured.
 */
@ConfigurationProperties(prefix = "cavale.admin")
public record AdminProperties(List<String> emails) {

    public AdminProperties {
        emails = emails == null
                ? List.of()
                : emails.stream()
                        .map(email -> email.trim().toLowerCase(Locale.ROOT))
                        .filter(email -> !email.isEmpty())
                        .toList();
    }

    /** Normalized-email membership test (emails arrive already normalized). */
    public boolean contains(String normalizedEmail) {
        return emails.contains(normalizedEmail);
    }
}
