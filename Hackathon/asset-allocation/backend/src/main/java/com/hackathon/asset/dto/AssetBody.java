package com.hackathon.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.hackathon.asset.entity.AssetCategory;
import com.hackathon.asset.entity.AssetCondition;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssetBody(
        @NotBlank(message = "Asset tag is required")
        @Pattern(regexp = "^[A-Za-z]{2,4}-\\d{3,6}$", message = "Use a tag like LT-1001") String assetTag,
        @NotBlank(message = "Serial number is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{4,30}$", message = "Serial number must be 4 to 30 letters, digits or dashes") String serialNumber,
        @NotBlank(message = "Name is required") @Size(max = 80, message = "Name must be at most 80 characters") String name,
        @NotNull(message = "Category is required") AssetCategory category,
        @Size(max = 40, message = "Brand must be at most 40 characters") String brand,
        @Size(max = 40, message = "Model must be at most 40 characters") String model,
        LocalDate purchaseDate,
        @DecimalMin(value = "0.00", message = "Price cannot be negative") BigDecimal purchasePrice,
        @NotNull(message = "Condition is required") AssetCondition condition,
        @Size(max = 300, message = "Notes must be at most 300 characters") String notes) {
}
