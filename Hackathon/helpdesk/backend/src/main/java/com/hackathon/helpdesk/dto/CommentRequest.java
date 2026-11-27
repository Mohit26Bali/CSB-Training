package com.hackathon.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** internal: an agent-only note the requester never sees. */
public record CommentRequest(@NotBlank(message = "Write something first") @Size(max = 1000, message = "Comment must be at most 1000 characters") String body,
                             Boolean internal) {
}
