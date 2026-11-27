package com.cartnest.repository;

import java.math.BigDecimal;

public record CustomerStats(Long userId, Long orders, BigDecimal spent) {
}
