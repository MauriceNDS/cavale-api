package com.cavale.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduled-task infrastructure (a single-threaded
 * TaskScheduler by default — fine while the only job is the Strava ingest)
 * and @Async execution (webhook events are acked immediately and processed
 * off the request thread; onboarding syncs run after the OAuth redirect).
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
