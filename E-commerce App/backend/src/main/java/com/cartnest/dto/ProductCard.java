package com.cartnest.dto;

import java.math.BigDecimal;

/** The short form of a product shown in grids, rails and suggestions. */
public record ProductCard(Long id, String slug, String title, String brand, String thumbnailUrl, BigDecimal price,
        BigDecimal mrp, int discountPercent, BigDecimal rating, int ratingCount, boolean inStock) {
}
