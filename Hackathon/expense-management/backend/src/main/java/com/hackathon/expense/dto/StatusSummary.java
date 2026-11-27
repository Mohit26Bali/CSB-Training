package com.hackathon.expense.dto;

import java.math.BigDecimal;

/** How many expenses are in each status and what they add up to. */
public record StatusSummary(long pendingCount, BigDecimal pendingAmount, long approvedCount,
                            BigDecimal approvedAmount, long rejectedCount, BigDecimal rejectedAmount) {
}
