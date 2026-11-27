package com.springlab.dto.challenge;

import com.springlab.entity.AttemptStatus;
import com.springlab.entity.Difficulty;

import java.time.Instant;
import java.util.List;

public record AttemptView(
        Long id,
        AttemptStatus status,
        String topicName,
        String topicSlug,
        Difficulty difficulty,
        int totalQuestions,
        Instant startedAt,
        List<QuestionView> questions) {
}
