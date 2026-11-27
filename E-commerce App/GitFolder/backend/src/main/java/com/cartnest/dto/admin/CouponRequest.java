package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cartnest.entity.DiscountType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CouponRequest(@NotBlank(message = "Code is required") @Pattern(regexp = "[A-Za-z0-9]{4,20}", message = "Code must be 4-20 letters or digits") String code,
        @NotBlank(message = "Description is required") @Size(max = 200) String description,
        @NotNull(message = "discountType is required (PERCENT or FLAT)") DiscountType discountType,
        @NotNull(message = "discountValue is required") @DecimalMin(value = "1", message = "discountValue must be at least 1") BigDecimal discountValue,
        @DecimalMin(value = "1", message = "maxDiscount must be at least 1") BigDecimal maxDiscount,
        @NotNull(message = "minOrderAmount is required") @DecimalMin(value = "0", message = "minOrderAmount cannot be negative") BigDecimal minOrderAmount,
        @NotNull(message = "validFrom is required") LocalDateTime validFrom,
        @NotNull(message = "validUntil is required") LocalDateTime validUntil,
        @Min(value = 1, message = "usageLimit must be at least 1") Integer usageLimit,
        @NotNull(message = "perUserLimit is required") @Min(value = 1, message = "perUserLimit must be between 1 and 100") @Max(value = 100, message = "perUserLimit must be between 1 and 100") Integer perUserLimit,
        Boolean active) {
}
