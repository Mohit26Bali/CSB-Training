package com.springlab.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "springlab.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {

    // Keep the secret out of logs / toString output.
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + ", secret=***]";
    }
}
