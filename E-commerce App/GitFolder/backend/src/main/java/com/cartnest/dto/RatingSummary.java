package com.cartnest.dto;

import java.math.BigDecimal;
import java.util.Map;

public record RatingSummary(BigDecimal average, int count, Map<Integer, Long> distribution) {
}
