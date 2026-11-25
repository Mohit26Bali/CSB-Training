package com.csb.cinescope.dto;

import java.util.List;

/**
 * Where one title can be watched in one country, grouped the way people decide: {@code stream} (included with a
 * subscription), {@code free} (free, with or without adverts), {@code rent} and {@code buy}. A group is an empty list
 * when nothing is offered. {@code link} is TMDB's page for the title in that country, which lists the same services and
 * sends people on to them; it is null when TMDB has no data for the country.
 *
 * <p>Built by {@code mapper/TitleMapper#providers} from TMDB's {@code /watch/providers} answer, returned by
 * {@code service/CatalogService#providers} through {@code GET /api/titles/{type}/{id}/providers}, and shown by the
 * frontend's WhereToWatch section. TMDB's availability data is supplied by JustWatch, which the UI must credit.
 */
public record WatchProviders(
        String region,
        String link,
        List<WatchProvider> stream,
        List<WatchProvider> free,
        List<WatchProvider> rent,
        List<WatchProvider> buy) {

    /** True when at least one service offers the title in any way. */
    public boolean available() {
        return !stream.isEmpty() || !free.isEmpty() || !rent.isEmpty() || !buy.isEmpty();
    }
}
