package com.icc.rca.dto;

/** The assignee, needed to assess a problem. Left out when the problem already has one. */
public record AssessRequest(Long assignedToId) {
}
