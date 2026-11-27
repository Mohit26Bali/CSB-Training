package com.icc.rca.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProblemDetailResponse(@NotNull ProblemResponse problem, @NotNull List<Long> incidentIds, @NotNull List<ActionResponse> actions) {
}
