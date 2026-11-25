package com.csb.cinescope.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/profile}: what a signed-in person may change about themselves. The username and email are fixed.
 * {@code region} is the country (two letters, such as IN or US) whose streaming services are shown first. Received by
 * {@code controller/ProfileController}, applied by {@code service/ProfileService#update}.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "display name is required")
        @Size(max = 60, message = "display name must be at most 60 characters")
        String displayName,

        @NotBlank(message = "region is required")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "region must be a two-letter country code such as IN or US")
        String region) {
}
