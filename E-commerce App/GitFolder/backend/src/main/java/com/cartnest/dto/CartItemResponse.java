package com.cartnest.dto;

import java.math.BigDecimal;

import com.cartnest.entity.VariantType;

public record CartItemResponse(Long id, Long productId, String productSlug, String title, String brand, String thumbnailUrl,
        Long variantId, String variantLabel, VariantType variantType, BigDecimal price, BigDecimal mrp,
        int quantity, int maxQuantity, BigDecimal lineTotal, String problem) {
}
