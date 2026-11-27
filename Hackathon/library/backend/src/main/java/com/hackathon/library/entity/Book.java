package com.hackathon.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 100)
    private String author;

    /** Digits only (dashes and spaces removed), unique. */
    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(length = 1000)
    private String description;

    private Integer publishedYear;

    @Column(nullable = false)
    private int totalCopies;

    protected Book() {
    }

    public Book(String title, String author, String isbn, String category, String description, Integer publishedYear,
                int totalCopies) {
        update(title, author, isbn, category, description, publishedYear, totalCopies);
    }

    public final void update(String title, String author, String isbn, String category, String description,
                             Integer publishedYear, int totalCopies) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.category = category;
        this.description = description;
        this.publishedYear = publishedYear;
        this.totalCopies = totalCopies;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public int getTotalCopies() {
        return totalCopies;
    }
}
