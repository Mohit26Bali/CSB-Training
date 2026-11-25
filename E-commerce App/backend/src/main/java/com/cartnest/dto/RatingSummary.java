package com.cartnest.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Average, count and how many reviews gave 5, 4, 3, 2 and 1 stars. */
public record RatingSummary(BigDecimal average, int count, Map<Integer, Long> distribution) {
}
