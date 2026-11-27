package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminProductRow(Long id, String slug, String title, String brand, String category, String thumbnailUrl,
        BigDecimal price, BigDecimal mrp, int discountPercent, int totalStock, int variantCount, boolean active,
        BigDecimal rating, int ratingCount, LocalDateTime updatedAt) {
}
