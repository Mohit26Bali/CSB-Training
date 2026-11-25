package com.csb.banking.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/auth/login}.
 *
 * <p>Received by {@code controller/AuthController}, checked by {@code service/AuthService#login}. The answer is an
 * {@link AuthResponse} holding the JWT.
 */
public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password) {
}
