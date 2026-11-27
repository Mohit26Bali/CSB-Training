package com.hackathon.library.dto;

import com.hackathon.library.entity.Book;

/** myLoanId / myLoanStatus: the signed-in member's own reservation or loan of this book (null for others). */
public record BookResponse(Long id, String title, String author, String isbn, String category, String description,
                           Integer publishedYear, int totalCopies, int availableCopies, String myLoanStatus,
                           Long myLoanId) {

    public static BookResponse from(Book b, int available, String myLoanStatus, Long myLoanId) {
        return new BookResponse(b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(), b.getCategory(), b.getDescription(),
                b.getPublishedYear(), b.getTotalCopies(), available, myLoanStatus, myLoanId);
    }
}
