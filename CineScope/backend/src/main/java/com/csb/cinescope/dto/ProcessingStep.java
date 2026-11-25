package com.csb.cinescope.dto;

/**
 * One stage of the comment-processing pipeline as the UI shows it ("How was this processed?"): the stage name (Cleaned, Spam
 * check, Profanity filter, Sentiment analysis, Decision), its outcome (PASSED, CHANGED, FLAGGED or HELD) and a sentence of detail.
 * Produced by {@code service/CommentProcessor}, stored in {@code comments.processing}, carried by {@link CommentView}.
 */
public record ProcessingStep(String name, String outcome, String detail) {
}
