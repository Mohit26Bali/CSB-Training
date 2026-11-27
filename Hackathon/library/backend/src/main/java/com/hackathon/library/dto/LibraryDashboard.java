package com.hackathon.library.dto;

import java.util.List;

public record LibraryDashboard(int titles, int copies, int available, long reserved, long issued, long overdue,
                               int finesCollected, List<CategoryCount> byCategory, List<LoanResponse> overdueLoans) {
}
