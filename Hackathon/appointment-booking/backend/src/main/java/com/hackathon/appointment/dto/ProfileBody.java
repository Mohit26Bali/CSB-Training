package com.hackathon.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileBody(
        @NotBlank(message = "Title is required") @Size(max = 60, message = "Title must be at most 60 characters") String title,
        @Size(max = 400, message = "Description must be at most 400 characters") String description,
        @Size(max = 100, message = "Location must be at most 100 characters") String location) {
}
