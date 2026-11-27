package com.hackathon.food.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DayRevenue(LocalDate date, long orders, BigDecimal revenue) {
}
