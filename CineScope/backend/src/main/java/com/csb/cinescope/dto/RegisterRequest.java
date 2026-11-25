package com.csb.cinescope.dto;

import com.csb.cinescope.security.StrongPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}. Received by {@code controller/AuthController}, turned into a
 * {@code entity/User} by {@code service/AuthService#register}. The password is capped at 20 characters (a product choice; BCrypt itself could use 72); its strength rules are
 * {@code security/PasswordPolicy} (applied through {@code @StrongPassword}).
 * Violations answer 400 with one message per field ({@code dto/ErrorResponse#fieldErrors}).
 */
public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be 3 to 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]*$", message = "username may contain letters, digits, '.', '_' and '-' only")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 100, message = "email must be at most 100 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 20, message = "password must be 8 to 20 characters")
        @StrongPassword
        String password,

        @NotBlank(message = "display name is required")
        @Size(max = 60, message = "display name must be at most 60 characters")
        String displayName) {
}
