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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One title on one user's watchlist (table {@code watchlist_items}): the user's status, optional 1-10 rating and note.
 *
 * <p>Relations: many items belong to one {@link User}. Created and changed by {@code service/WatchlistService} (and, when demo
 * data is switched on, once at start-up by {@code config/DemoDataInitializer}), read
 * through {@code repository/WatchlistItemRepository}. The title is identified by its TMDB id plus media type (a movie and a
 * show can share an id); {@code title}, {@code posterPath} and {@code releaseYear} are a snapshot taken from TMDB when the
 * item was first added, so listing the watchlist never needs TMDB. The unique constraint on (user, id, type) means a title
 * can be on a list only once, even if two requests arrive together.
 */
@Entity
@Table(name = "watchlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_watchlist_user_title", columnNames = {"user_id", "tmdb_id", "media_type"}),
        indexes = @Index(name = "idx_watchlist_user_status", columnList = "user_id, status, added_at"))
public class WatchlistItem {

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

    @Column(nullable = false)
    private String title;

    @Column(name = "poster_path")
    private String posterPath;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WatchStatus status;

    /** 1-10, only while the status is WATCHED. */
    private Integer rating;

    @Column(length = 500)
    private String note;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WatchlistItem() {
        // required by JPA
    }

    public WatchlistItem(User user, long tmdbId, String mediaType, String title, String posterPath, Integer releaseYear) {
        this.user = user;
        this.tmdbId = tmdbId;
        this.mediaType = mediaType;
        this.title = title;
        this.posterPath = posterPath;
        this.releaseYear = releaseYear;
        this.status = WatchStatus.WANT;
    }

    /** Replaces the user-controlled fields; the caller has already validated the combination. */
    public void update(WatchStatus status, Integer rating, String note) {
        this.status = status;
        this.rating = rating;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        addedAt = now;
        updatedAt = now;
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

    public String getTitle() {
        return title;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public WatchStatus getStatus() {
        return status;
    }

    public Integer getRating() {
        return rating;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
