package com.csb.cinescope.dto;

import java.util.List;

/**
 * Every episode of a show with its rating, laid out season by season: the data behind the episode-rating heatmap on a show's page.
 * {@code best} and {@code worst} are the highest- and lowest-rated episodes (null when fewer than two episodes have been rated, as
 * then there is nothing to compare). {@code average} is the mean of all rated episodes. {@code truncated} is true when the show has
 * more seasons than the page shows. Built by {@code service/EpisodeRatingsService}; returned by {@code GET /api/titles/tv/{id}/episodes}.
 */
public record EpisodeHeatmap(long id, String name, int seasonCount, int episodeCount, int ratedCount, Double average, Episode best, Episode worst,
                             boolean truncated, List<Season> seasons) {

    /**
     * One season. {@code average} is the mean of its rated episodes (null if none); {@code unavailable} means TMDB could not supply
     * the season right now (its episode list is then empty) while the other seasons are still shown.
     */
    public record Season(int number, Double average, boolean unavailable, List<Episode> episodes) {
    }

    /**
     * One episode. {@code rating} is TMDB's average vote out of 10, or null when nobody has rated it yet (an unaired episode, or
     * one with no votes): such an episode is shown as blank, never as a zero. {@code aired} is false for a date still to come or unknown.
     */
    public record Episode(int season, int number, String name, String airDate, Double rating, int votes, boolean aired) {
    }
}
