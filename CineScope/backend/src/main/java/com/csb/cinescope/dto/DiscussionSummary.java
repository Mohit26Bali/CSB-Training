package com.csb.cinescope.dto;

import java.util.List;

/**
 * What the comments of one title add up to - the "processing" results the title page shows above the discussion.
 *
 * <ul>
 *   <li>{@code histogram}: ten numbers, how many people gave 1, 2 ... 10; {@code averageRating} is their mean.</li>
 *   <li>{@code tmdbRating}: TMDB's own score. {@code communityScore} blends the two with a Bayesian average,
 *       {@code (n * average + 5 * tmdb) / (n + 5)}: with no ratings it equals TMDB's, and the more people rate, the more the
 *       community's opinion counts.</li>
 *   <li>{@code sentiment}: how many published comments are positive, neutral and negative, with whole-number percentages.</li>
 *   <li>{@code topics}: the words people mention most (common words and emotion words left out).</li>
 * </ul>
 * Only published comments count. Built by {@code service/CommentService#summary}.
 */
public record DiscussionSummary(
        int commentCount,
        int replyCount,
        int ratingCount,
        Double averageRating,
        List<Integer> histogram,
        Double tmdbRating,
        Double communityScore,
        SentimentShare sentiment,
        List<Topic> topics) {

    /** Counts and percentages of the three moods (the percentages add up to 100 when there is any comment). */
    public record SentimentShare(int positive, int neutral, int negative, int positivePercent, int neutralPercent, int negativePercent) {
    }

    public record Topic(String word, int count) {
    }
}
