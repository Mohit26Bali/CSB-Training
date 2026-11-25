package com.csb.cinescope.dto;

/**
 * A movie or TV show as it appears in rows, grids and recommendation lists.
 *
 * <p>{@code type} is {@code "movie"} or {@code "tv"} and, together with {@code id}, identifies the title for the detail
 * endpoint ({@code GET /api/titles/{type}/{id}}). {@code rating} is TMDB's 0-10 user score (0 when unrated).
 * Built by {@code mapper/TitleMapper#summary}.
 */
public record TitleSummary(
        long id,
        String type,
        String title,
        String overview,
        String posterPath,
        String backdropPath,
        Integer year,
        double rating) {
}
