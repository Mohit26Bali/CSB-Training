package com.hackathon.helpdesk.dto;

import java.time.LocalDateTime;

import com.hackathon.helpdesk.entity.TicketComment;

public record CommentResponse(Long id, String authorUsername, String authorName, String authorRole, String body,
                              boolean internal, LocalDateTime createdAt) {

    public static CommentResponse from(TicketComment c) {
        return new CommentResponse(c.getId(), c.getAuthor().getUsername(), c.getAuthor().getFullname(),
                c.getAuthor().getRole().name(), c.getBody(), c.isInternalNote(), c.getCreatedAt());
    }
}
