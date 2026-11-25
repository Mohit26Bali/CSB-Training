package com.csb.cinescope.dto;

/**
 * One line of a person's filmography: a movie or show they worked on. {@code type} ("movie" or "tv") with {@code id}
 * identifies the title for {@code GET /api/titles/{type}/{id}} and the frontend's title page. {@code role} is the
 * character they played (acting credits) or the job(s) they did, such as "Director, Writer" (crew credits); it can be
 * null when TMDB does not say. {@code year} is null for unreleased or undated titles.
 *
 * <p>Part of {@link PersonDetail}; built by {@code mapper/TitleMapper#person}.
 */
public record PersonCredit(long id, String type, String title, String posterPath, Integer year, double rating, String role) {
}
