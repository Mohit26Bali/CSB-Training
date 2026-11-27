package com.hackathon.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.expense.entity.Expense;

public record ExpenseResponse(Long id, String employeeUsername, String employeeName, LocalDate expenseDate,
                              String category, String categoryLabel, BigDecimal amount, String description,
                              String status, LocalDateTime submittedAt, String decidedBy, LocalDateTime decidedAt,
                              String rejectionReason) {

    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(e.getId(), e.getEmployee().getUsername(), e.getEmployee().getFullname(),
                e.getExpenseDate(), e.getCategory().name(), e.getCategory().getLabel(), e.getAmount(),
                e.getDescription(), e.getStatus().name(), e.getSubmittedAt(), e.getDecidedBy(), e.getDecidedAt(),
                e.getRejectionReason());
    }
}
