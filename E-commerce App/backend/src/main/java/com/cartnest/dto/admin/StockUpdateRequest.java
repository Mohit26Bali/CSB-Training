package com.cartnest.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockUpdateRequest(@NotNull(message = "stock is required") @Min(value = 0, message = "Stock cannot be negative")
        @Max(value = 100000, message = "Stock must be at most 100000") Integer stock) {
}
