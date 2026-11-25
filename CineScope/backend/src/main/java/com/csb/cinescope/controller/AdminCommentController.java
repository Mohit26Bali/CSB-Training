package com.csb.cinescope.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.CommentView;
import com.csb.cinescope.dto.ModerationRequest;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Moderation of comments for administrators: the queue of comments the processing pipeline held back, and the decision on each.
 * Every URL under {@code /api/admin/} needs ROLE_ADMIN (config/SecurityConfig), and {@code service/CommentService} repeats the
 * check on these two methods with {@code @PreAuthorize}. The frontend page is pages/AdminModerationPage.
 *
 * <pre>
 * GET /api/admin/comments?status=PENDING        the queue (PENDING by default; REJECTED and PUBLISHED can be looked at too)
 * PUT /api/admin/comments/{id}/moderation       {"action": "APPROVE" | "REJECT"}
 * </pre>
 */
@Tag(name = "Admin moderation", description = "Review held comments (administrators only)")
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final CommentService comments;

    public AdminCommentController(CommentService comments) {
        this.comments = comments;
    }

    @Operation(summary = "Moderation queue", description = "Comments by status, newest first. Default PENDING: those held by the processing pipeline.")
    @GetMapping
    public PageResult<CommentView> queue(@RequestParam(defaultValue = "PENDING") String status,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         Authentication authentication) {
        return comments.moderationQueue(status, page, size, authentication.getName());
    }

    @Operation(summary = "Approve or reject a comment", description = "APPROVE publishes it; REJECT hides it from everyone except its author.")
    @PutMapping("/{id}/moderation")
    public CommentView moderate(@PathVariable long id, @Valid @RequestBody ModerationRequest request, Authentication authentication) {
        return comments.moderate(id, request.action(), authentication.getName());
    }
}
