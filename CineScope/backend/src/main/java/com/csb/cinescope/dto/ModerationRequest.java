package com.csb.cinescope.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code PUT /api/admin/comments/{id}/moderation}: {@code action} is APPROVE or REJECT. */
public record ModerationRequest(
        @NotBlank(message = "action is required")
        String action) {
}
