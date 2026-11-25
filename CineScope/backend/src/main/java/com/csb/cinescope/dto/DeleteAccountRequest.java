package com.csb.cinescope.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code DELETE /api/profile}: deleting an account is permanent, so the person confirms with their password. Applied
 * by {@code service/ProfileService#deleteAccount}.
 */
public record DeleteAccountRequest(
        @NotBlank(message = "password is required")
        String password) {
}
