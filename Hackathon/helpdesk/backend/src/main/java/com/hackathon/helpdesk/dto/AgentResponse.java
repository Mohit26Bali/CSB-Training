package com.hackathon.helpdesk.dto;

public record AgentResponse(String username, String fullname, long activeTickets) {
}
