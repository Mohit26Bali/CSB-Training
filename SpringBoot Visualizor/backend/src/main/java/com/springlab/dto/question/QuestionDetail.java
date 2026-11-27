package com.springlab.dto.question;

import com.springlab.entity.Difficulty;

import java.time.Instant;
import java.util.List;

/** Teacher/admin view of a question: includes correct flags and the explanation. Never sent to students. */
public record QuestionDetail(
        Long id,
        String text,
        String explanation,
        Long topicId,
        String topicName,
        String topicSlug,
        Difficulty difficulty,
        boolean active,
        String createdBy,
        boolean editable,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<OptionDetail> options) {

    public record OptionDetail(Long id, String text, boolean correct) {
    }
}
