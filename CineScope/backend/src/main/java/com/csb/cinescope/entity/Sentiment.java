package com.csb.cinescope.entity;

/**
 * The tone of a comment as judged by {@code service/CommentProcessor}: POSITIVE, NEGATIVE, or NEUTRAL when there is no clear
 * emotion. Stored as text in {@code comments.sentiment} next to the numeric score; the title page adds the labels up into the
 * "audience mood" bar.
 */
public enum Sentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}
