package com.springlab.dto.progress;

import com.springlab.dto.challenge.AttemptSummary;

import java.util.List;

/** Computed from the caller's submitted attempts. Skipped questions count as incorrect. */
public record ProgressResponse(
        long attemptsCompleted,
        long questionsAnswered,
        long correctAnswers,
        double accuracyPercent,
        List<Breakdown> byTopic,
        List<Breakdown> byDifficulty,
        List<AttemptSummary> recentAttempts) {

    public record Breakdown(String key, String label, long questions, long correct, double accuracyPercent) {
    }
}
