package com.springlab.dto.challenge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Questions that are not listed count as skipped (incorrect). */
public record SubmitAnswersRequest(@NotNull @Size(max = 20) List<@Valid @NotNull AnswerSubmission> answers) {

    public record AnswerSubmission(@NotNull Long questionId, @NotNull Long optionId) {
    }
}
