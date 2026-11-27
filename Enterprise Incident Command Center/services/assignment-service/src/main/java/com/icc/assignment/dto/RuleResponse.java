package com.icc.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record RuleResponse(@NotNull Long id, @NotNull String category, @NotNull Long teamId, @NotNull boolean active) {
}
