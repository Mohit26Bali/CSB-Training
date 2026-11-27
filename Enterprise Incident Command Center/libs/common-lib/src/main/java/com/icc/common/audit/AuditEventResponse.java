package com.icc.common.audit;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** actorId, entityId, details and correlationId may be missing, so they carry no @NotNull (D28). */
public record AuditEventResponse(@NotNull Long id, Long actorId, @NotNull String action, @NotNull String entityType,
                                 Long entityId, String details, String correlationId, @NotNull LocalDateTime createdAt) {
}
