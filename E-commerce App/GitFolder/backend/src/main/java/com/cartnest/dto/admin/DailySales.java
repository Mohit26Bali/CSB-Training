package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySales(LocalDate date, long orders, BigDecimal revenue) {
}
