package com.hackathon.expense.dto;

import java.math.BigDecimal;

public record CategoryTotal(String category, String categoryLabel, long count, BigDecimal amount) {
}
