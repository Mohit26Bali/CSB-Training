package com.hackathon.complaints.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintRequest(
        @NotBlank(message = "Subject is required") @Size(max = 120, message = "Subject must be at most 120 characters") String subject,
        @NotBlank(message = "Please describe what went wrong") @Size(max = 2000, message = "Description must be at most 2000 characters") String description,
        @NotBlank(message = "Choose a category") String category,
        @NotBlank(message = "Choose a priority") String priority) {
}
