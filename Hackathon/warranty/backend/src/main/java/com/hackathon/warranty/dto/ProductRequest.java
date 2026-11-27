package com.hackathon.warranty.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "Product name is required") @Size(max = 120, message = "Name must be at most 120 characters") String name,
        @NotBlank(message = "Brand is required") @Size(max = 80, message = "Brand must be at most 80 characters") String brand,
        @NotBlank(message = "Category is required") String category,
        @NotBlank(message = "Serial number is required")
        @Size(min = 3, max = 40, message = "Serial number must be 3 to 40 characters")
        @Pattern(regexp = "[A-Za-z0-9._/-]*", message = "Serial number may contain only letters, digits and . _ / -") String serialNumber,
        @NotNull(message = "Purchase date is required") LocalDate purchaseDate,
        @NotNull(message = "Warranty period is required")
        @Min(value = 1, message = "Warranty must be at least 1 month")
        @Max(value = 60, message = "Warranty can be at most 60 months") Integer warrantyMonths) {
}
