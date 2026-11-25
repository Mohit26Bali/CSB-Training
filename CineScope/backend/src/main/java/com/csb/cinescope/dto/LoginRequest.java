package com.csb.cinescope.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/login}; checked by {@code service/AuthService#login}. */
public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password) {
}
