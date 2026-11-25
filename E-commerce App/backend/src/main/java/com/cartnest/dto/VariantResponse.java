package com.cartnest.dto;

import java.math.BigDecimal;

public record VariantResponse(Long id, String sku, String label, BigDecimal price, BigDecimal mrp, int discountPercent,
        int stock) {
}
