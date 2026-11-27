package com.hackathon.procurement.dto;

import java.math.BigDecimal;

public record CategoryTotal(String category, String categoryLabel, long count, BigDecimal amount) {
}
