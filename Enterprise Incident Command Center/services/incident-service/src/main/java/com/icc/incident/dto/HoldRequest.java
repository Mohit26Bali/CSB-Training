package com.icc.incident.dto;

import com.icc.incident.entity.HoldReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Putting an incident on hold. The comment is required when waiting for the caller, because the caller must be told
 * what is needed (it is saved as a comment the caller can see). For the other reasons it is optional (saved as a work note).
 */
public record HoldRequest(@NotNull HoldReason reason, @Size(max = 4000) String comment) {
}
