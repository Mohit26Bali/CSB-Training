package com.icc.incident.dto;

import com.icc.incident.entity.HoldReason;
import com.icc.incident.entity.IncidentState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * The incident as the API sends it. The annotations describe the shape for the OpenAPI contract the frontend is
 * generated from (D24): @NotNull means "always there and never null", the nullable marker means "always there, but
 * the value is null until it is set" (for example assignedToId before an engineer accepts). They do not change the JSON.
 */
public record IncidentResponse(
        @NotNull Long id,
        @NotNull String number,
        @NotNull String shortDescription,
        @NotNull String description,
        @NotNull String category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String subcategory,
        @NotNull int impact,
        @NotNull int urgency,
        @NotNull int priority,
        @NotNull IncidentState state,
        @NotNull Long reporterId,
        @NotNull Long assignedTeamId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long assignedToId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) HoldReason holdReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String resolutionCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String resolutionNotes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long majorIncidentId,
        @NotNull LocalDateTime createdAt,
        @NotNull LocalDateTime updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime resolvedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime closedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String syncStatus) {
}
