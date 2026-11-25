package com.csb.cinescope.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A comment or reply as the browser sees it. {@code type} + {@code tmdbId} say which title it is on. {@code body} is the text
 * after processing (bad words masked). {@code sentiment} is POSITIVE, NEUTRAL or NEGATIVE with its numeric {@code sentimentScore}
 * (-1 to +1); {@code steps} is how the pipeline reached that. {@code status} is PUBLISHED, PENDING or REJECTED (only the author
 * and administrators ever receive the last two). {@code helpfulByMe} and {@code mine} are relative to whoever is asking.
 * {@code replies} holds the visible replies of a top-level comment, oldest first; it is empty for a reply.
 *
 * <p>Built by {@code service/CommentService}; returned by {@code controller/CommentController} and
 * {@code controller/AdminCommentController}.
 */
public record CommentView(
        long id,
        Long parentId,
        long tmdbId,
        String type,
        String authorUsername,
        String authorName,
        String body,
        Integer rating,
        String sentiment,
        double sentimentScore,
        String status,
        int helpfulCount,
        boolean helpfulByMe,
        boolean mine,
        LocalDateTime createdAt,
        List<ProcessingStep> steps,
        List<CommentView> replies) {
}
