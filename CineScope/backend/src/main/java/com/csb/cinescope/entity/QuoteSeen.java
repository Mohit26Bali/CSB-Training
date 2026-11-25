package com.csb.cinescope.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * "This person has already been shown this quote" (table {@code quote_seen}, migration V5). Written and cleared by
 * {@code service/QuoteService}, which uses it so a quiz never repeats a quote until the whole category has been seen. Holds plain
 * ids rather than links to {@link User} and {@link Quote}: the database cascades deletes, and the service never needs the objects.
 */
@Entity
@Table(name = "quote_seen")
public class QuoteSeen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "quote_id", nullable = false)
    private long quoteId;

    @Column(name = "seen_at", nullable = false)
    private LocalDateTime seenAt;

    protected QuoteSeen() {
        // required by JPA
    }

    public QuoteSeen(long userId, long quoteId) {
        this.userId = userId;
        this.quoteId = quoteId;
    }

    @PrePersist
    void onCreate() {
        seenAt = LocalDateTime.now();
    }

    public long getUserId() {
        return userId;
    }

    public long getQuoteId() {
        return quoteId;
    }
}
