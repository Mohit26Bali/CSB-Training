package com.hackathon.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Spending is counted in the month a request was approved, and only while it stays approved, ordered or delivered. */
public record ProcurementDashboard(List<StatusTotal> byStatus, BigDecimal monthlyBudget, BigDecimal spentThisMonth,
                                   BigDecimal remainingThisMonth, List<CategoryTotal> spendByCategory,
                                   LocalDateTime oldestPendingAt) {
}
