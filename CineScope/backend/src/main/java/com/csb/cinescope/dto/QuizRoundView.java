package com.csb.cinescope.dto;

import java.util.List;

/** A newly started round: its id, category and its questions (without answers). Returned by {@code POST /api/quiz/rounds}. */
public record QuizRoundView(long id, String category, int total, List<QuizQuestionView> questions) {
}
