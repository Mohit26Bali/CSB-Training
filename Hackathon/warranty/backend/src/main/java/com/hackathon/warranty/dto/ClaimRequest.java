package com.hackathon.warranty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** priority is optional (LOW, MEDIUM or HIGH) and defaults to MEDIUM. */
public record ClaimRequest(
        @NotNull(message = "Choose a product") Long productId,
        @NotBlank(message = "Please describe the issue")
        @Size(max = 1000, message = "Description must be at most 1000 characters") String issueDescription,
        String priority) {
}
