package com.springlab.dto.challenge;

import com.springlab.entity.Difficulty;

import java.time.Instant;
import java.util.List;

/** Only ever returned for a SUBMITTED attempt: this is where correct answers and explanations are revealed. */
public record AttemptResult(
        Long id,
        String topicName,
        String topicSlug,
        Difficulty difficulty,
        int score,
        int totalQuestions,
        double percentage,
        Instant startedAt,
        Instant submittedAt,
        List<QuestionResult> questions) {

    public record OptionResult(Long id, String text, boolean correct, boolean selected) {
    }

    public record QuestionResult(
            Long questionId,
            int position,
            String text,
            Difficulty difficulty,
            String topicName,
            List<OptionResult> options,
            Long selectedOptionId,
            boolean answered,
            boolean correct,
            String explanation) {
    }
}
