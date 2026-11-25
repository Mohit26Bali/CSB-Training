package com.csb.cinescope.dto;

/**
 * What happens when a question is answered: right or wrong, the right option, the points earned (of which {@code speedBonus} was a
 * bonus for a quick right answer in the poster game, otherwise 0) and the running score and streak, and the truth about the question
 * ({@link Reveal}) so the page can show the film's poster. {@code finished} is true after the last question; only then does the round
 * count on the leaderboard. Returned by {@code POST /api/quiz/rounds/{id}/answers}.
 */
public record QuizAnswerResult(boolean correct, int correctIndex, int points, int speedBonus, int score, int streak, int bestStreak, int correctCount, int answered, int total,
                               boolean finished, Reveal reveal) {

    /**
     * Where the quote or poster is from; {@code tmdbId} and {@code mediaType} let the page load the poster. {@code speaker} is null for
     * the cast and poster games, which are about the title itself.
     */
    public record Reveal(String title, int year, String speaker, long tmdbId, String mediaType, String category) {
    }
}
