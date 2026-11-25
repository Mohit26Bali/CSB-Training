package com.csb.cinescope.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a new comment, a reply, or an edit. {@code rating} (1 to 10) is optional and only allowed on a top-level comment;
 * the service ({@code service/CommentService}) refuses it on a reply. The text then goes through
 * {@code service/CommentProcessor}.
 */
public record CommentRequest(
        @NotBlank(message = "comment is required")
        @Size(min = 2, max = 1000, message = "comment must be 2 to 1000 characters")
        String body,

        @Min(value = 1, message = "rating must be between 1 and 10")
        @Max(value = 10, message = "rating must be between 1 and 10")
        Integer rating) {
}
