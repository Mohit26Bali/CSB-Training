package com.csb.cinescope.dto;

/** An actor in a title's cast. {@code id} is the person's id for {@code GET /api/people/{id}} (the frontend's person page). */
public record CastMember(long id, String name, String character, String profilePath) {
}
