package com.cartnest.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandRequest(@NotBlank(message = "Name is required") @Size(max = 80, message = "Name must be at most 80 characters") String name) {
}
