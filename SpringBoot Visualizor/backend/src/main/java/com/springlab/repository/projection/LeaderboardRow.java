package com.springlab.repository.projection;

public record LeaderboardRow(String username, long attempts, long points, long questions) {
}
