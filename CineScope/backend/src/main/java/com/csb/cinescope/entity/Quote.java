package com.csb.cinescope.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A famous line from a film or show (table {@code quotes}, migration V4). Filled by {@code config/QuoteSeeder}; read by
 * {@code repository/QuoteRepository}. The title is identified like everywhere else in the app, by media type and TMDB id, so
 * pictures and cast come from TMDB while the line and the speaker are ours (TMDB has no dialogue).
 */
@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String category;

    @Column(name = "media_type", nullable = false, length = 5)
    private String mediaType;

    @Column(name = "tmdb_id", nullable = false)
    private long tmdbId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    @Column(nullable = false, length = 100)
    private String speaker;

    @Column(nullable = false, length = 500)
    private String line;

    protected Quote() {
        // required by JPA
    }

    public Quote(QuoteCategory category, String mediaType, long tmdbId, String title, int releaseYear, String speaker, String line) {
        this.category = category.name();
        this.mediaType = mediaType;
        this.tmdbId = tmdbId;
        this.title = title;
        this.releaseYear = releaseYear;
        this.speaker = speaker;
        this.line = line;
    }

    public Long getId() {
        return id;
    }

    public QuoteCategory getCategory() {
        return QuoteCategory.valueOf(category);
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getTmdbId() {
        return tmdbId;
    }

    public String getTitle() {
        return title;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getLine() {
        return line;
    }
}
