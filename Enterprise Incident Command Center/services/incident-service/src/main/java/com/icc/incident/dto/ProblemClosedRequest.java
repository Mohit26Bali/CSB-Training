package com.icc.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sent by the RCA service when a problem that covers this incident is closed. */
public record ProblemClosedRequest(@NotBlank @Size(max = 40) String problemNumber, @Size(max = 4000) String closeNotes) {
}
