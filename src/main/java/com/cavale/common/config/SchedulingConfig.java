package com.cavale.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduled-task infrastructure (a single-threaded
 * TaskScheduler by default — fine while the only job is the Strava ingest).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
