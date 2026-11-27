package com.springlab.coding.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the execution backend from configuration. DISABLED is the default and needs nothing; WSL must be requested explicitly.
 * Creating either backend starts no process and contacts nothing: availability is checked lazily, on first use.
 */
@Configuration
@EnableConfigurationProperties(ExecutionProperties.class)
public class ExecutionConfig {

    @Bean
    ExecutionBackend executionBackend(ExecutionProperties properties, ObjectMapper mapper) {
        return switch (properties.backend()) {
            case DISABLED -> new DisabledBackend();
            case WSL -> new WslBackend(properties.wsl(), mapper);
        };
    }
}
