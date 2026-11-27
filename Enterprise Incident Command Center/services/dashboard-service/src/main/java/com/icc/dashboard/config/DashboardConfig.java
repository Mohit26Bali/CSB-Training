package com.icc.dashboard.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class DashboardConfig {

    /**
     * The same settings for the three circuit breakers (D10).
     * After 5 calls with half of them failing the breaker opens for 10 seconds, so a service
     * that is down is not asked again for every request. A call that takes over 4 seconds counts as failed.
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerSettings() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(4))
                        .build())
                .build());
    }

    /** The threads that make the three calls at the same time. */
    @Bean(name = "dashboardExecutor", destroyMethod = "shutdown")
    public ExecutorService dashboardExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(6, task -> {
            Thread thread = new Thread(task, "dashboard-call-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
