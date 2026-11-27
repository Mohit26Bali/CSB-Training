package com.hackathon.library.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.library.entity.Loan;

/** overdue / daysOverdue / fine: for an ISSUED loan the fine is what it would cost if returned today. */
public record LoanResponse(Long id, Long bookId, String bookTitle, String bookAuthor, String category,
                           String memberUsername, String memberName, String status, LocalDateTime reservedAt,
                           LocalDateTime issuedAt, LocalDate dueDate, LocalDateTime returnedAt, boolean overdue,
                           long daysOverdue, int fine, String issuedBy, String returnedBy) {

    public static LoanResponse from(Loan l, long daysOverdue, int fine) {
        return new LoanResponse(l.getId(), l.getBook().getId(), l.getBook().getTitle(), l.getBook().getAuthor(),
                l.getBook().getCategory(), l.getMember().getUsername(), l.getMember().getFullname(), l.getStatus().name(),
                l.getReservedAt(), l.getIssuedAt(), l.getDueDate(), l.getReturnedAt(), daysOverdue > 0, daysOverdue, fine,
                l.getIssuedBy(), l.getReturnedBy());
    }
}
