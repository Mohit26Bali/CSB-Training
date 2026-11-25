package com.cartnest.dto.admin;

import java.math.BigDecimal;

public record TopProduct(Long productId, String slug, String title, String thumbnailUrl, long unitsSold, BigDecimal revenue) {
}
