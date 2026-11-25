package com.csb.cinescope.dto;

import java.util.List;

/**
 * A film series (a TMDB "collection", for example the Avengers films) and how much of it one person has watched.
 * {@code released} counts the parts already out, {@code watched} the ones this person marked watched, and {@code missing} the
 * released parts that are not on their watchlist at all (what the "add the rest" button would add). Built by
 * {@code service/FranchiseService}; returned by {@code GET /api/titles/movie/{id}/franchise}.
 */
public record FranchiseView(long id, String name, String posterPath, int total, int released, int watched, int missing, List<Part> parts) {

    /** One film of the series, oldest first. {@code status} is the person's watchlist status (WANT, WATCHING, WATCHED) or null. */
    public record Part(long id, String title, Integer year, String releaseDate, String posterPath, boolean released, String status) {
    }
}
