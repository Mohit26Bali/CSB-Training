package com.icc.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TeamResponse(@NotNull Long id, @NotNull String name,
                           @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description) {
}
