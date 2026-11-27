package com.springlab.dto.question;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.springlab.entity.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Used for both create (POST) and full replacement (PUT). Options carry an id when they already exist (edited in
 * place, so past answers keep pointing at them); options without an id are new; existing options that are omitted
 * are removed. "version" is required on update (optimistic locking) and ignored on create.
 */
public record QuestionRequest(
        @NotBlank @Size(max = 2000) String text,
        @NotBlank @Size(max = 4000) String explanation,
        @NotNull Long topicId,
        @NotNull Difficulty difficulty,
        @NotNull @Size(min = 2, max = 6) List<@Valid @NotNull OptionInput> options,
        Long version) {

    public record OptionInput(Long id, @NotBlank @Size(max = 500) String text, boolean correct) {
    }

    @JsonIgnore
    @AssertTrue(message = "must contain exactly one correct option")
    public boolean isExactlyOneCorrectOption() {
        return options == null || options.stream().filter(o -> o != null && o.correct()).count() == 1;
    }
}
