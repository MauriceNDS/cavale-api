package com.cavale.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cavale.user.repository.UserRepository;

/**
 * Registers {@link AccountAccessFilter} as a servlet-filter bean. Boot
 * auto-registers {@code Filter} beans at the lowest precedence, so it runs
 * after Spring Security's chain (a valid JWT is already authenticated) and
 * before the controller.
 *
 * <p>Deliberately a standalone {@code @Configuration} — not imported by any
 * {@code @WebMvcTest} slice — so those web-slice tests never try to build the
 * filter (which needs the data layer).
 */
@Configuration
public class AccountAccessConfig {

    @Bean
    AccountAccessFilter accountAccessFilter(UserRepository userRepository) {
        return new AccountAccessFilter(userRepository);
    }
}
