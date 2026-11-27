package com.cartnest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull(message = "Choose a rating from 1 to 5 stars")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5") Integer rating,
        @NotBlank(message = "Write a few words about the product")
        @Size(min = 10, max = 1000, message = "The review must be 10 to 1000 characters") String comment) {
}
