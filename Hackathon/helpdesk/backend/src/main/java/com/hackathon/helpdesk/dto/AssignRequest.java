package com.hackathon.helpdesk.dto;

/** assignee: the agent's username, or empty to put the ticket back in the unassigned queue. */
public record AssignRequest(String assignee) {
}
