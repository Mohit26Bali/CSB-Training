package com.csb.cinescope.dto;

/**
 * One search hit. {@code type} is {@code "movie"}, {@code "tv"} or {@code "person"}: the search box searches all three
 * at once. For a person, {@code title} is the name, {@code imagePath} the profile photo and {@code knownFor} a short
 * list of their best-known titles; {@code year} and {@code rating} are then empty / 0.
 */
public record SearchItem(
        long id,
        String type,
        String title,
        String imagePath,
        Integer year,
        double rating,
        String overview,
        String knownFor) {
}
