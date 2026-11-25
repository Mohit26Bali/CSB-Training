package com.csb.banking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on {@code @Scheduled} methods. Currently the only one is {@code service/IdempotencyService#purgeExpired},
 * which deletes old idempotency keys.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
