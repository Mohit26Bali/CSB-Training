package com.springlab.dto.challenge;

import com.springlab.entity.Difficulty;

import java.util.List;

/**
 * The student-facing form of a question: no correct-answer information and no explanation.
 * Also used for the teacher "preview", so the preview shows exactly what a student receives.
 */
public record QuestionView(
        Long id,
        int position,
        String text,
        Difficulty difficulty,
        String topicName,
        String topicSlug,
        List<OptionView> options) {
}
