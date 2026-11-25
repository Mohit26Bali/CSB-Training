package com.csb.cinescope.tmdb;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builds the query parameters of a TMDB call as a sorted map.
 *
 * <p>Sorted on purpose: {@link TmdbHttpGateway} uses the parameters as part of its cache key, and the same request
 * must always produce the same key whatever order the parameters were added in. Null and blank values are skipped, so
 * optional filters can be passed straight through.
 */
public final class TmdbQuery {

    private final SortedMap<String, String> params = new TreeMap<>();

    private TmdbQuery() {
    }

    public static TmdbQuery create() {
        return new TmdbQuery();
    }

    public TmdbQuery with(String name, Object value) {
        if (value != null && !value.toString().isBlank()) {
            params.put(name, value.toString());
        }
        return this;
    }

    public SortedMap<String, String> build() {
        return params;
    }
}
