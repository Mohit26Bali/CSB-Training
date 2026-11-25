package com.csb.cinescope.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.CommentRequest;
import com.csb.cinescope.dto.CommentView;
import com.csb.cinescope.dto.DiscussionSummary;
import com.csb.cinescope.dto.HelpfulResult;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Reviews and discussion on titles. No logic here: {@code service/CommentService} applies the rules and runs every text through
 * {@code service/CommentProcessor}. Everything needs a login (config/SecurityConfig); the person is the one named by the token.
 *
 * <pre>
 * GET    /api/titles/{type}/{id}/comments     the comments (?sort=helpful|newest|positive|critical &amp;page= &amp;size=), with replies
 * GET    /api/titles/{type}/{id}/discussion   ratings histogram, blended score, audience mood, topics
 * POST   /api/titles/{type}/{id}/comments     write a comment, optionally with a 1-10 rating
 * POST   /api/comments/{id}/replies           reply to a comment
 * PUT    /api/comments/{id}                   edit my comment
 * DELETE /api/comments/{id}                   delete my comment (an administrator may delete any)
 * POST   /api/comments/{id}/helpful           mark helpful / take the mark back
 * </pre>
 * The frontend section is components/Discussion on pages/TitlePage.
 */
@Tag(name = "Comments", description = "Reviews, replies, ratings and helpful marks on titles (login required)")
@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @Operation(summary = "Comments on a title", description = "Top-level comments with their replies. sort: helpful (default), newest, positive, critical.")
    @GetMapping("/titles/{type}/{id}/comments")
    public PageResult<CommentView> list(@PathVariable String type, @PathVariable long id,
                                        @RequestParam(defaultValue = "helpful") String sort,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        Authentication authentication) {
        return comments.list(type, id, sort, page, size, authentication.getName());
    }

    @Operation(summary = "What the discussion adds up to", description = "Rating histogram and average, the community score blended with TMDB's, the audience mood and the topics people mention.")
    @GetMapping("/titles/{type}/{id}/discussion")
    public DiscussionSummary summary(@PathVariable String type, @PathVariable long id) {
        return comments.summary(type, id);
    }

    @Operation(summary = "Write a comment", description = "Optional rating 1-10 (one rated review per title). The text is cleaned, checked for spam and bad language and scored for sentiment; the answer shows how. A comment held for review is visible only to its author and the moderators.")
    @PostMapping("/titles/{type}/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView post(@PathVariable String type, @PathVariable long id, @Valid @RequestBody CommentRequest request, Authentication authentication) {
        return comments.post(type, id, request, authentication.getName());
    }

    @Operation(summary = "Reply to a comment", description = "One level deep: you cannot reply to a reply. A reply cannot carry a rating.")
    @PostMapping("/comments/{id}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView reply(@PathVariable long id, @Valid @RequestBody CommentRequest request, Authentication authentication) {
        return comments.reply(id, request, authentication.getName());
    }

    @Operation(summary = "Edit my comment", description = "Only the author. The new text is processed again.")
    @PutMapping("/comments/{id}")
    public CommentView edit(@PathVariable long id, @Valid @RequestBody CommentRequest request, Authentication authentication) {
        return comments.edit(id, request, authentication.getName());
    }

    @Operation(summary = "Delete a comment", description = "The author, or an administrator. Its replies and helpful marks go with it.")
    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication authentication) {
        comments.delete(id, authentication.getName());
    }

    @Operation(summary = "Mark helpful", description = "Toggles: marks the comment helpful, or removes your mark. You cannot mark your own comment.")
    @PostMapping("/comments/{id}/helpful")
    public HelpfulResult helpful(@PathVariable long id, Authentication authentication) {
        return comments.toggleHelpful(id, authentication.getName());
    }
}
