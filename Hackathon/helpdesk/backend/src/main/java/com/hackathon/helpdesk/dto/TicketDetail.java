package com.hackathon.helpdesk.dto;

import java.util.List;

public record TicketDetail(TicketResponse ticket, List<CommentResponse> comments) {
}
