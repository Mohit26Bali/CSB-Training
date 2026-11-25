package com.csb.cinescope.dto;

/** A TMDB genre (for example 28 = Action). Used for genre chips on detail pages and the genre filter of browse. */
public record Genre(int id, String name) {
}
