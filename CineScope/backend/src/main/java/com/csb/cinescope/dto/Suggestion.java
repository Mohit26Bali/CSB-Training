package com.csb.cinescope.dto;

/**
 * One title offered by the "what to watch tonight" feature: the same facts as a {@link TitleSummary} plus
 * {@code onWatchlist}, true when the signed-in user already has it on their list as "want to watch" (such titles are
 * preferred). Part of {@link Recommendation}; built by {@code service/RecommendationService}.
 */
public record Suggestion(
        long id,
        String type,
        String title,
        String overview,
        String posterPath,
        String backdropPath,
        Integer year,
        double rating,
        boolean onWatchlist) {
}
