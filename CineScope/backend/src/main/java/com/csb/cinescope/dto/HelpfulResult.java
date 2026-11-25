package com.csb.cinescope.dto;

/** The answer to marking a comment helpful: whether it is now marked by me, and the new total. */
public record HelpfulResult(boolean helpful, int helpfulCount) {
}
