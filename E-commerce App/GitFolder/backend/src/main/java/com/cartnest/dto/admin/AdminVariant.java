package com.cartnest.dto.admin;

import java.math.BigDecimal;

public record AdminVariant(Long id, String sku, String label, BigDecimal price, BigDecimal mrp, int stock) {
}
