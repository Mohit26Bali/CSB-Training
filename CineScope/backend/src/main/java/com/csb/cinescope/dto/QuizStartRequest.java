package com.csb.cinescope.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/quiz/rounds}: which game to play. HOLLYWOOD, TV, BOLLYWOOD and ANIME are the famous-lines quiz in that
 * category and ALL mixes them; CAST ("which title has these actors") and POSTER ("name the blurred poster") are the two picture-and-cast
 * games. See {@code service/QuizService}.
 */
public record QuizStartRequest(
        @NotBlank @Pattern(regexp = "ALL|HOLLYWOOD|TV|BOLLYWOOD|ANIME|CAST|POSTER", message = "category must be ALL, HOLLYWOOD, TV, BOLLYWOOD, ANIME, CAST or POSTER") String category) {
}
