package com.cartnest.dto.admin;

public record LowStockItem(Long variantId, Long productId, String productSlug, String title, String label, String sku, int stock) {
}
