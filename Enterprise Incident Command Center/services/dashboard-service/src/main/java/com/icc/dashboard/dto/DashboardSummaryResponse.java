package com.icc.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * The summary for the admin dashboard. A part is null when its service could not be reached,
 * and then "unavailable" names the missing part (ARCHITECTURE: partial results, never a failed page).
 * The annotations only describe the shape for the OpenAPI contract (D28); the JSON is unchanged.
 */
public record DashboardSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) IncidentSummary incidents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) MajorIncidentSummary majorIncidents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ProblemSummary problems,
        @NotNull List<String> unavailable) {

    public record IncidentSummary(@NotNull long total, @NotNull long open, @NotNull long criticalOpen,
                                  @NotNull long resolvedToday,
                                  @NotNull Map<String, Long> byState, @NotNull Map<Integer, Long> byPriority) {
    }

    public record MajorIncidentSummary(@NotNull long total, @NotNull long active, @NotNull Map<String, Long> byStatus) {
    }

    public record ProblemSummary(@NotNull long total, @NotNull long open, @NotNull long openActions,
                                 @NotNull Map<String, Long> byStatus) {
    }
}
