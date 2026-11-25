package com.csb.cinescope.dto;

import java.util.List;

/**
 * The answer to "what should I watch tonight?": one {@code pick}, a few {@code alternatives}, and why.
 *
 * <p>{@code pick} is null when nothing matches the wishes (then {@code alternatives} is empty too and the frontend
 * suggests loosening a filter). {@code details} (runtime, genres, age rating, trailers) and {@code providers} (where to
 * watch it in the default country) describe the pick; each is null if TMDB could not supply it, which never fails the
 * whole answer. {@code reasons} are short human phrases such as "Comedy mood" or "Streaming in India".
 * {@code considered} is how many candidates were weighed, and {@code seed} the number that made the draw: sending the
 * same wishes with the same seed returns the same answer, and a new seed gives "show me another".
 *
 * <p>Returned by {@code GET /api/tonight} ({@code controller/TonightController}), built by
 * {@code service/RecommendationService}.
 */
public record Recommendation(
        Suggestion pick,
        TitleDetail details,
        WatchProviders providers,
        List<Suggestion> alternatives,
        List<String> reasons,
        int considered,
        long seed) {
}
