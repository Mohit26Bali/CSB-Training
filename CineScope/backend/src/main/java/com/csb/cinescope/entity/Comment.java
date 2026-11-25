package com.csb.cinescope.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One comment or reply on a title (table {@code comments}).
 *
 * <p>Relations: belongs to the {@link User} who wrote it; a reply points at its top-level comment through {@code parent}
 * (replies are one level deep). The title is identified by {@code mediaType} + {@code tmdbId}, like the catalogue and the
 * watchlist. {@code rating} (1-10) exists only on top-level comments. {@code body} is the text AFTER processing (cleaned, bad
 * words masked); {@code sentiment}, {@code sentimentScore}, {@code status} and {@code processing} are what
 * {@code service/CommentProcessor} decided, kept so the page can show how the comment was judged. Created and changed by
 * {@code service/CommentService} (and, when demo data is on, {@code config/DemoDataInitializer}).
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    /** "movie" or "tv". */
    @Column(name = "media_type", nullable = false, length = 5)
    private String mediaType;

    /** Null for a top-level comment; the comment being answered for a reply. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = 1000)
    private String body;

    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sentiment sentiment;

    @Column(name = "sentiment_score", nullable = false)
    private double sentimentScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommentStatus status;

    /** One "stage|outcome|detail" line per processing stage (see {@code service/CommentProcessor}). */
    @Column(nullable = false, length = 1000)
    private String processing;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Comment() {
        // required by JPA
    }

    public Comment(User user, long tmdbId, String mediaType, Comment parent, String body, Integer rating, Sentiment sentiment,
                   double sentimentScore, CommentStatus status, String processing) {
        this.user = user;
        this.tmdbId = tmdbId;
        this.mediaType = mediaType;
        this.parent = parent;
        this.body = body;
        this.rating = rating;
        this.sentiment = sentiment;
        this.sentimentScore = sentimentScore;
        this.status = status;
        this.processing = processing;
    }

    /** Replaces what the author may change, together with the freshly computed judgement of the new text. */
    public void edit(String body, Integer rating, Sentiment sentiment, double sentimentScore, CommentStatus status, String processing) {
        this.body = body;
        this.rating = rating;
        this.sentiment = sentiment;
        this.sentimentScore = sentimentScore;
        this.status = status;
        this.processing = processing;
    }

    /** A moderator's decision on a held comment; the new processing text includes their note. */
    public void moderate(CommentStatus status, String processing) {
        this.status = status;
        this.processing = processing;
    }

    public void setHelpfulCount(int helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    /** Only for demo data: makes a comment look as if it was written earlier. */
    public void backdate(LocalDateTime when) {
        this.createdAt = when;
        this.updatedAt = when;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Long getTmdbId() {
        return tmdbId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public Comment getParent() {
        return parent;
    }

    public String getBody() {
        return body;
    }

    public Integer getRating() {
        return rating;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public double getSentimentScore() {
        return sentimentScore;
    }

    public CommentStatus getStatus() {
        return status;
    }

    public String getProcessing() {
        return processing;
    }

    public int getHelpfulCount() {
        return helpfulCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
