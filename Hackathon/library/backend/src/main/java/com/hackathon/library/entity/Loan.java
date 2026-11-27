package com.hackathon.library.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One member's hold on one copy of a book: reserved, then issued, then returned (or the reservation is cancelled). */
@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LoanStatus status = LoanStatus.RESERVED;

    @Column(nullable = false)
    private LocalDateTime reservedAt = LocalDateTime.now();

    private LocalDateTime issuedAt;

    private LocalDate dueDate;

    private LocalDateTime returnedAt;

    private String issuedBy;

    private String returnedBy;

    /** Fine charged when the book came back late, in rupees. */
    private Integer fine;

    protected Loan() {
    }

    public Loan(Book book, User member) {
        this.book = book;
        this.member = member;
    }

    public void issue(String librarian, LocalDate dueDate) {
        this.status = LoanStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
        this.issuedBy = librarian;
        this.dueDate = dueDate;
    }

    public void giveBack(String librarian, int fine) {
        this.status = LoanStatus.RETURNED;
        this.returnedAt = LocalDateTime.now();
        this.returnedBy = librarian;
        this.fine = fine;
    }

    public void cancel() {
        this.status = LoanStatus.CANCELLED;
    }

    public boolean isHolding() {
        return LoanStatus.HOLDING.contains(status);
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public User getMember() {
        return member;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(LocalDateTime reservedAt) {
        this.reservedAt = reservedAt;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(LocalDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public String getReturnedBy() {
        return returnedBy;
    }

    public Integer getFine() {
        return fine;
    }

    public void setFine(Integer fine) {
        this.fine = fine;
    }
}
