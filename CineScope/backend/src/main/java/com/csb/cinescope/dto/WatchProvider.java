package com.csb.cinescope.dto;

/**
 * One service a title can be watched on (Netflix, Prime Video, JioHotstar ...). {@code logoPath} is a TMDB image path,
 * turned into a URL by the frontend (lib/images.ts) exactly like posters. Part of {@link WatchProviders}.
 */
public record WatchProvider(int id, String name, String logoPath) {
}
