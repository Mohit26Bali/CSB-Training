package com.cartnest.dto;

import java.math.BigDecimal;

public record OrderItemResponse(String productSlug, String title, String variantLabel, String thumbnailUrl, BigDecimal unitPrice,
        BigDecimal unitMrp, int quantity, BigDecimal lineTotal) {
}
