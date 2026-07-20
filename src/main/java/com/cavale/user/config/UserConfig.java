package com.cavale.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wires the user feature's configuration properties. */
@Configuration
@EnableConfigurationProperties({AdminProperties.class, DevLoginProperties.class})
public class UserConfig {
}
