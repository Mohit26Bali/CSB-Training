package com.cartnest.dto;

import java.math.BigDecimal;

public record PriceRange(BigDecimal min, BigDecimal max) {
}
