package com.csb.cinescope.dto;

/** A key crew member: the director of a movie, or a creator of a TV show ({@code job} says which). */
public record CrewMember(long id, String name, String job) {
}
