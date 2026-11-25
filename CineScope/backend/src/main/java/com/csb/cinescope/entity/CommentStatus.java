package com.csb.cinescope.entity;

/**
 * Where a comment stands. PUBLISHED comments are visible to everyone; PENDING ones were held back by the processing pipeline
 * ({@code service/CommentProcessor}) and wait for an administrator, who either approves them (PUBLISHED) or rejects them
 * (REJECTED). The author always sees their own pending and rejected comments, with the reason. Stored as text in
 * {@code comments.status}.
 */
public enum CommentStatus {
    PUBLISHED,
    PENDING,
    REJECTED
}
