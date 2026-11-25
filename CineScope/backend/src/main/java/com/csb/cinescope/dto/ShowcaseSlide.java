package com.csb.cinescope.dto;

/**
 * One slide of the picture carousel on the sign-in and sign-up screens: a title's backdrop and poster (TMDB image paths, turned
 * into URLs by the frontend) with a famous line from it. {@code speaker} is who says the line, which is not always the lead
 * ("The Joker", "Gabbar Singh"). {@code quoteId} identifies the quote: the browser remembers the ids it has shown and sends them
 * back so the next visit shows different ones. {@code id} and {@code type} are the TMDB identity of the film or show, and
 * {@code category} is HOLLYWOOD, TV, BOLLYWOOD or ANIME.
 *
 * <p>Built by {@code service/ShowcaseService} and returned by {@code GET /api/showcase} ({@code controller/ShowcaseController}),
 * the one catalogue endpoint that needs no login because the screens that show it are the login screens.
 */
public record ShowcaseSlide(long quoteId, long id, String type, String category, String title, Integer year, String backdropPath, String posterPath, String quote, String speaker) {
}
