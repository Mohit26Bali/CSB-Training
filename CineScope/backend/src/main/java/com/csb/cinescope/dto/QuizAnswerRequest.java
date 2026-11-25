package com.csb.cinescope.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body of {@code POST /api/quiz/rounds/{id}/answers}: which question (its {@code seq}) and which of the four options was chosen (0-3). */
public record QuizAnswerRequest(@Min(1) int seq, @Min(0) @Max(3) int choice) {
}
