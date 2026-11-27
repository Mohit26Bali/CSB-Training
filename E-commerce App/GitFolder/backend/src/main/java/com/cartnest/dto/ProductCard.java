package com.cartnest.dto;

import java.math.BigDecimal;

public record ProductCard(Long id, String slug, String title, String brand, String thumbnailUrl, BigDecimal price,
        BigDecimal mrp, int discountPercent, BigDecimal rating, int ratingCount, boolean inStock) {
}
