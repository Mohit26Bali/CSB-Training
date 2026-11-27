package com.hackathon.food.dto;

import java.math.BigDecimal;

import com.hackathon.food.entity.MenuCategory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MenuBody(
        @NotBlank(message = "Name is required") @Size(max = 60, message = "Name must be at most 60 characters") String name,
        @Size(max = 300, message = "Description must be at most 300 characters") String description,
        @NotNull(message = "Category is required") MenuCategory category,
        @NotNull(message = "Price is required") @DecimalMin(value = "10.00", message = "Price must be at least 10") @DecimalMax(value = "5000.00", message = "Price must be at most 5000") BigDecimal price,
        Boolean vegetarian,
        Boolean active) {
}
