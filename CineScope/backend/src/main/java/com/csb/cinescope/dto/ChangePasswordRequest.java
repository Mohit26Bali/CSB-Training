package com.csb.cinescope.dto;

import com.csb.cinescope.security.StrongPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/profile/password}. The current password proves it is really the owner (a stolen, still-valid
 * token alone is not enough); the new one must obey the same strength rules as at sign-up ({@code security/PasswordPolicy}).
 * Applied by {@code service/ProfileService#changePassword}.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "current password is required")
        String currentPassword,

        @NotBlank(message = "new password is required")
        @Size(min = 8, max = 20, message = "password must be 8 to 20 characters")
        @StrongPassword
        String newPassword) {
}
