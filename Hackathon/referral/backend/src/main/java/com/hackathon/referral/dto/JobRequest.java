package com.hackathon.referral.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record JobRequest(
        @NotBlank(message = "Title is required") @Size(max = 120, message = "Title must be at most 120 characters") String title,
        @NotBlank(message = "Department is required") @Size(max = 80, message = "Department must be at most 80 characters") String department,
        @NotBlank(message = "Location is required") @Size(max = 80, message = "Location must be at most 80 characters") String location,
        @NotBlank(message = "Description is required") @Size(max = 2000, message = "Description must be at most 2000 characters") String description,
        @NotEmpty(message = "Add at least one skill") @Size(max = 12, message = "At most 12 skills") List<@NotBlank(message = "Skills cannot be blank") @Size(max = 30, message = "A skill must be at most 30 characters") String> skills) {
}
