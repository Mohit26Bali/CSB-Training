package com.icc.incident.dto;

import com.icc.incident.entity.CommentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(@NotNull CommentType type, @NotBlank @Size(max = 4000) String body) {
}
