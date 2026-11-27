package com.icc.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRuleRequest(@NotNull Long teamId) {
}
