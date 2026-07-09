package com.cavale.integration.strava;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StravaProperties.class)
public class StravaConfig {
}
