package com.hackathon.procurement.dto;

import java.math.BigDecimal;

public record StatusTotal(String status, long count, BigDecimal amount) {
}
