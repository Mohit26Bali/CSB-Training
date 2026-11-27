package com.icc.majorincident.dto;

import com.icc.majorincident.entity.MajorIncidentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * The annotations describe the shape for the OpenAPI contract the frontend is generated from (D24, D28):
 * @NotNull = always there and never null; the nullable marker = always sent, but null until it is set.
 * They do not change the JSON.
 */
public record MajorIncidentResponse(
        @NotNull Long id,
        @NotNull Long incidentId,
        @NotNull String summary,
        @NotNull String impactSummary,
        @NotNull Long commanderId,
        @NotNull MajorIncidentStatus status,
        @NotNull Long declaredById,
        @NotNull LocalDateTime declaredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime resolvedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime closedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String syncStatus) {
}
