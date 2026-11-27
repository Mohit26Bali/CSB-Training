package com.springlab.dto.progress;

/**
 * Ranking is by total correct answers across submitted attempts (points), then by fewer questions attempted
 * (higher accuracy), then by username. Equal points and questions share a rank. No email or id is exposed.
 */
public record LeaderboardEntry(int rank, String username, long points, long attempts, long questions,
                               double accuracyPercent) {
}
