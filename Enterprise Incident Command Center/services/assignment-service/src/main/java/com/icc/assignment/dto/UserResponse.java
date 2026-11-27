package com.icc.assignment.dto;

import com.icc.common.jwt.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserResponse(@NotNull Long id, @NotNull String username, @NotNull String fullName, @NotNull String email,
                           @NotNull Role role,
                           @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long teamId) {
}
