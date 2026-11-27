package com.springlab.dto.challenge;

import com.springlab.entity.AttemptStatus;
import com.springlab.entity.Difficulty;

import java.time.Instant;

public record AttemptSummary(
        Long id,
        AttemptStatus status,
        String topicName,
        String topicSlug,
        Difficulty difficulty,
        int totalQuestions,
        Integer score,
        Instant startedAt,
        Instant submittedAt) {
}
