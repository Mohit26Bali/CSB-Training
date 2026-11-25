package com.csb.cinescope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * The {@code tmdb.*} settings from application.properties (and backend/config/application.properties for the secret).
 *
 * <p>Read by {@link TmdbClientConfig} (builds the HTTP client: base URL, bearer token, timeouts). The token is
 * validated at startup, so a missing token stops the application with a message naming {@code tmdb.read-token}
 * instead of failing later with confusing 401 errors from TMDB.
 */
@Validated
@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
        @NotBlank(message = "set tmdb.read-token in backend/config/application.properties or the TMDB_READ_TOKEN "
                + "environment variable (your TMDB 'API Read Access Token')") String readToken,
        @NotBlank String baseUrl,
        @Positive int connectTimeoutMs,
        @Positive int readTimeoutMs) {
}
