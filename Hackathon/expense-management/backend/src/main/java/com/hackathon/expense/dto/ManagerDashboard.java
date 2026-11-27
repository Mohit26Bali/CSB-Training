package com.hackathon.expense.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ManagerDashboard(StatusSummary summary, List<CategoryTotal> approvedByCategory,
                               LocalDateTime oldestPendingAt) {
}
