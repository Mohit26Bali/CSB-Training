package com.hackathon.helpdesk.dto;

import java.util.List;

public record AgentDashboard(long open, long inProgress, long resolved, long closed, long unassigned, long mine,
                             long breached, long atRisk, double averageResolutionHours, List<Count> activeByPriority,
                             List<Count> byCategory) {
}
