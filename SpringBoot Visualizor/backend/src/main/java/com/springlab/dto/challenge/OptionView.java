package com.springlab.dto.challenge;

/** What a student sees of an answer option. Deliberately has no "correct" flag. */
public record OptionView(Long id, String text) {
}
