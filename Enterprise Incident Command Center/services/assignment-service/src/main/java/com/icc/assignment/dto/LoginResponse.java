package com.icc.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record LoginResponse(@NotNull String token, @NotNull String tokenType, @NotNull long expiresInSeconds,
                            @NotNull UserResponse user) {
}
