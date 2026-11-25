package com.csb.cinescope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * The {@code cinescope.*} settings: the defaults used when a request does not say otherwise.
 *
 * <p>{@code defaultRegion} (ISO country code, India by default) decides which country's "now playing" and "upcoming"
 * lists are shown and, in a later phase, whose streaming availability. {@code language} is the TMDB text language.
 * Read by {@code service/CatalogService}.
 */
@Validated
@ConfigurationProperties(prefix = "cinescope")
public record CineScopeProperties(@NotBlank String defaultRegion, @NotBlank String language) {
}
