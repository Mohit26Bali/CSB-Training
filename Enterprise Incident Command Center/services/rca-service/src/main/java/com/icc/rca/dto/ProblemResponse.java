package com.icc.rca.dto;

import com.icc.rca.entity.ProblemAction;
import com.icc.rca.entity.ProblemStatus;
import com.icc.rca.entity.ResolutionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The annotations describe the shape for the OpenAPI contract the frontend is generated from (D24, D28):
 * @NotNull = always there and never null; the nullable marker = always sent, but null until it is set.
 * They do not change the JSON.
 * availableActions are the buttons that may be pressed now, so the screen does not repeat the rules (D31).
 */
public record ProblemResponse(
        @NotNull Long id,
        @NotNull String number,
        @NotNull String title,
        @NotNull String description,
        @NotNull ProblemStatus status,
        @NotNull List<ProblemAction> availableActions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String rootCause,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String rootCauseCategory,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String workaround,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String fixNotes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ResolutionCode resolutionCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long duplicateOfId,
        @NotNull Boolean workaroundApplied,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long assignedToId,
        @NotNull Integer impact,
        @NotNull Integer urgency,
        @NotNull Integer priority,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long majorIncidentId,
        @NotNull Integer reopenCount,
        @NotNull Long createdById,
        @NotNull LocalDateTime createdAt,
        @NotNull LocalDateTime updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime resolvedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime closedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String syncStatus) {
}
