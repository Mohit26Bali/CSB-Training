package com.csb.cinescope.tmdb;

import java.util.SortedMap;

import tools.jackson.databind.JsonNode;

/**
 * Everything the application can ask TMDB. Each method fetches one TMDB path (for example {@code /movie/550}) with
 * query parameters and returns TMDB's JSON untouched; turning it into CineScope's own response shapes is the job of
 * {@code mapper/TitleMapper}.
 *
 * <p>The three methods differ only in how long the answer may be cached ({@code config/CacheConfig}): {@code lists}
 * for fast-changing lists and searches, {@code detail} for a single movie / show / person, {@code reference} for
 * almost-static data such as genre lists. Failures surface as {@code exception/NotFoundException} or
 * {@code exception/UpstreamException}.
 */
public interface TmdbGateway {

    JsonNode lists(String path, SortedMap<String, String> query);

    JsonNode detail(String path, SortedMap<String, String> query);

    JsonNode reference(String path, SortedMap<String, String> query);
}
