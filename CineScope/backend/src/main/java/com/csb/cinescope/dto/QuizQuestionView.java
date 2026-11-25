package com.csb.cinescope.dto;

import java.util.List;

/**
 * A question as the player sees it: what is asked ({@code prompt}), the clue and four options. The correct answer is deliberately not
 * here; it is revealed in {@link QuizAnswerResult} once the question has been answered. {@code kind} says what the clue is:
 * <ul>
 * <li>TITLE: {@code line} is a famous quote, name the film or show;</li>
 * <li>SPEAKER: {@code line} is a quote, name who says it;</li>
 * <li>CAST: {@code actors} are three of the people in it, name the film or show;</li>
 * <li>POSTER: {@code posterPath} is a TMDB image path (shown blurred by the page), name the film or show.</li>
 * </ul>
 * The fields that do not apply to a kind are null.
 */
public record QuizQuestionView(int seq, String kind, String prompt, String line, List<String> actors, String posterPath, List<String> options) {
}
