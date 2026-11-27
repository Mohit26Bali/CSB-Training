package com.hackathon.library.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create or edit a book (the librarian's form). */
public record BookBody(
        @NotBlank(message = "Title is required") @Size(max = 150, message = "Title must be at most 150 characters") String title,
        @NotBlank(message = "Author is required") @Size(max = 100, message = "Author must be at most 100 characters") String author,
        @NotBlank(message = "ISBN is required") @Size(max = 20, message = "ISBN is too long")
        @Pattern(regexp = "^$|^(?:[0-9][ -]?){9}[0-9Xx]$|^(?:[0-9][ -]?){12}[0-9]$", message = "Enter a valid 10 or 13 digit ISBN") String isbn,
        @NotBlank(message = "Category is required") @Size(max = 40, message = "Category must be at most 40 characters") String category,
        @Size(max = 1000, message = "Description must be at most 1000 characters") String description,
        @Min(value = 1000, message = "Enter a four-digit year") @Max(value = 2100, message = "Enter a four-digit year") Integer publishedYear,
        @NotNull(message = "Number of copies is required") @Min(value = 1, message = "There must be at least 1 copy") @Max(value = 100, message = "At most 100 copies") Integer totalCopies) {
}
