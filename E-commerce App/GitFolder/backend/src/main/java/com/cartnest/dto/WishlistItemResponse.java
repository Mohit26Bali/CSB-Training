package com.cartnest.dto;

import java.time.LocalDateTime;

public record WishlistItemResponse(ProductCard product, LocalDateTime addedAt) {
}
