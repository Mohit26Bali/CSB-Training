package com.icc.incident.dto;

import com.icc.incident.entity.CommentType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CommentResponse(@NotNull Long id, @NotNull Long incidentId, @NotNull CommentType type, @NotNull String body,
                              @NotNull Long authorId, @NotNull LocalDateTime createdAt) {
}
