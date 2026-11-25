package com.cartnest.dto.admin;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VariantInput(Long id,
        @NotBlank(message = "SKU is required") @Pattern(regexp = "[A-Za-z0-9-]{3,40}", message = "SKU must be 3-40 letters, digits or dashes") String sku,
        @NotBlank(message = "Variant label is required") @Size(max = 40, message = "Variant label must be at most 40 characters") String label,
        @NotNull(message = "Price is required") @DecimalMin(value = "1", message = "Price must be at least 1") BigDecimal price,
        @NotNull(message = "MRP is required") @DecimalMin(value = "1", message = "MRP must be at least 1") BigDecimal mrp,
        @NotNull(message = "Stock is required") @Min(value = 0, message = "Stock cannot be negative") @Max(value = 100000, message = "Stock must be at most 100000") Integer stock) {
}
