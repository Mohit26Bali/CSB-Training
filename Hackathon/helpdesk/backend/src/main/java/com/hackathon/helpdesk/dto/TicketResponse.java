package com.hackathon.helpdesk.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.hackathon.helpdesk.entity.Ticket;
import com.hackathon.helpdesk.entity.TicketStatus;

/**
 * number is the readable reference (HD-1042). slaState: ON_TRACK, AT_RISK (under a quarter of the time left) or BREACHED
 * while the ticket is active, then MET or MISSED once it is resolved. hoursLeft is negative when overdue.
 */
public record TicketResponse(Long id, String number, String title, String description, String category, String priority,
                             String status, String requesterUsername, String requesterName, String assigneeUsername,
                             String assigneeName, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime dueAt,
                             LocalDateTime resolvedAt, LocalDateTime closedAt, String resolution, String slaState,
                             long hoursLeft, List<String> allowedNext, long commentCount) {

    public static String numberOf(Long id) {
        return "HD-" + (1000 + id);
    }

    public static String slaState(Ticket t, LocalDateTime now) {
        if (t.getStatus().isActive()) {
            if (now.isAfter(t.getDueAt())) {
                return "BREACHED";
            }
            long total = Duration.between(t.getCreatedAt(), t.getDueAt()).toMinutes();
            long left = Duration.between(now, t.getDueAt()).toMinutes();
            return left * 4 < total ? "AT_RISK" : "ON_TRACK";
        }
        LocalDateTime done = t.getResolvedAt() != null ? t.getResolvedAt() : t.getClosedAt();
        return done != null && !done.isAfter(t.getDueAt()) ? "MET" : "MISSED";
    }

    public static TicketResponse from(Ticket t, long commentCount, LocalDateTime now) {
        long hoursLeft = t.getStatus().isActive() ? Duration.between(now, t.getDueAt()).toHours() : 0;
        return new TicketResponse(t.getId(), numberOf(t.getId()), t.getTitle(), t.getDescription(), t.getCategory().name(),
                t.getPriority().name(), t.getStatus().name(), t.getRequester().getUsername(), t.getRequester().getFullname(),
                t.getAssignee() == null ? null : t.getAssignee().getUsername(),
                t.getAssignee() == null ? null : t.getAssignee().getFullname(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getDueAt(), t.getResolvedAt(), t.getClosedAt(), t.getResolution(), slaState(t, now), hoursLeft,
                t.getStatus().next().stream().map(TicketStatus::name).toList(), commentCount);
    }
}
