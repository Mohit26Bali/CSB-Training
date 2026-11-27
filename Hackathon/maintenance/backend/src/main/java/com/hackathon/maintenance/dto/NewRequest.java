package com.hackathon.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewRequest(
        @NotBlank(message = "Choose a category") String category,
        @NotBlank(message = "Please describe the problem") @Size(max = 1000, message = "Description must be at most 1000 characters") String description,
        @NotBlank(message = "Say where the problem is") @Size(max = 100, message = "Location must be at most 100 characters") String location,
        @NotBlank(message = "Choose a priority") String priority) {
}
