package com.icc.assignment.dto;

import com.icc.common.jwt.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserSummaryResponse(@NotNull Long id, @NotNull String fullName, @NotNull Role role,
                                  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long teamId) {
}
