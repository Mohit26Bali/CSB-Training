package com.cartnest.repository;

import java.math.BigDecimal;

public record ProductSales(Long productId, Long units, BigDecimal revenue) {
}
