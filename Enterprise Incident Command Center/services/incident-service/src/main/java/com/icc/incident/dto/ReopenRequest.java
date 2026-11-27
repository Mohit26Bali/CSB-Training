package com.icc.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the incident is being reopened. It is saved as a comment, so the history shows the reason. */
public record ReopenRequest(@NotBlank @Size(max = 4000) String reason) {
}
