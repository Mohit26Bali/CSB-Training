package com.csb.cinescope.dto;

import java.util.List;

/**
 * Everything the detail page shows about one movie or TV show: facts, cast, trailers and similar titles.
 * Built by {@code mapper/TitleMapper#detail} from a single TMDB call with appended credits, videos and recommendations.
 *
 * <p>Fields that do not apply are null: {@code runtime} / {@code budget} / {@code revenue} belong to movies,
 * {@code seasons} / {@code episodes} to TV shows. {@code certification} is the age rating for the default region
 * (India), falling back to the US rating. {@code familyFriendly} is true only when that rating means suitable for everyone
 * (see {@code mapper/AgeRating}); a title with no rating is not family friendly. {@code directors} holds the director(s) of a movie or the creator(s) of a show.
 */
public record TitleDetail(
        long id,
        String type,
        String title,
        String tagline,
        String overview,
        String posterPath,
        String backdropPath,
        Integer year,
        String releaseDate,
        Integer runtime,
        double rating,
        int voteCount,
        String status,
        String language,
        String certification,
        List<Genre> genres,
        List<CrewMember> directors,
        List<CastMember> cast,
        List<Video> videos,
        List<TitleSummary> similar,
        Long budget,
        Long revenue,
        Integer seasons,
        Integer episodes,
        String homepage,
        boolean familyFriendly) {
}
