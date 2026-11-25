package com.csb.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Remembers that a money request with a given {@code Idempotency-Key} was already processed, together with the
 * response that was sent (table {@code idempotency_keys}).
 *
 * <p>How it is used (see {@code service/IdempotencyService}): the row is inserted at the START of the money
 * transaction and completed with the response before that same transaction commits, so "money moved" and "key
 * recorded" are one atomic fact. The unique constraint on (key, username) is what stops two identical requests
 * arriving at the same moment from both being processed - the second insert waits for, then collides with, the
 * first. A retry with a known key replays {@link #getResponseBody()} instead of moving money again.
 *
 * <p>Keys are scoped per user, so one customer's key can never replay another customer's response. Old rows are
 * purged by {@code IdempotencyService#purgeExpired}. Stored by {@code repository/IdempotencyRepository}.
 */
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key_user", columnNames = {"idempotency_key", "username"}))
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, length = 50)
    private String username;

    /** SHA-256 of the request content; a reused key with different content is rejected. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /** JSON of the response of the first execution; null only while that first execution is still running. */
    @Column(name = "response_body", length = 4000)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected IdempotencyRecord() {
        // required by JPA
    }

    public IdempotencyRecord(String idempotencyKey, String username, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.username = username;
        this.requestHash = requestHash;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getUsername() {
        return username;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
}
