package com.hackathon.scholarship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Approving and rejecting both need a reason: the student reads it. */
public record DecisionRequest(
        @NotBlank(message = "A reason is required for the decision")
        @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {
}
