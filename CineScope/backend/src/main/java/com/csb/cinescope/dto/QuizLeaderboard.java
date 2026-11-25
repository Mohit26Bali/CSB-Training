package com.csb.cinescope.dto;

import java.util.List;

/**
 * The best scores in a category: the top ten (each person once, with their best finished round) and, if the person asking has
 * played, their own place. Returned by {@code GET /api/quiz/leaderboard}.
 */
public record QuizLeaderboard(String category, List<Row> entries, Row you) {

    /** One place on the board. {@code you} marks the row that belongs to the person asking. */
    public record Row(int rank, String username, String displayName, int score, boolean you) {
    }
}
