package com.hackathon.event.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotBlank(message = "Title is required") @Size(max = 120, message = "Title must be at most 120 characters") String title,
        @NotBlank(message = "Category is required") String category,
        @NotBlank(message = "Venue is required") @Size(max = 120, message = "Venue must be at most 120 characters") String venue,
        @NotBlank(message = "Description is required") @Size(max = 2000, message = "Description must be at most 2000 characters") String description,
        @NotNull(message = "Start time is required") LocalDateTime startsAt,
        @NotNull(message = "End time is required") LocalDateTime endsAt,
        @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") @Max(value = 5000, message = "Capacity must be at most 5000") Integer capacity) {
}
