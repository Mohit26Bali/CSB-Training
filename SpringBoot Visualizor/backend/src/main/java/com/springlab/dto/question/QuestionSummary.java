package com.springlab.dto.question;

import com.springlab.entity.Difficulty;

import java.time.Instant;

public record QuestionSummary(
        Long id,
        String text,
        String topicName,
        String topicSlug,
        Difficulty difficulty,
        boolean active,
        String createdBy,
        boolean editable,
        Instant updatedAt) {
}
