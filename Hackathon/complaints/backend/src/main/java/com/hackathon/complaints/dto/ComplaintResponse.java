package com.hackathon.complaints.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.hackathon.complaints.entity.Complaint;
import com.hackathon.complaints.entity.ComplaintStatus;

/** number is the readable reference (CMP-2042). ageDays is how long an active complaint has been waiting. */
public record ComplaintResponse(Long id, String number, String subject, String description, String category, String priority,
                                String status, String customerUsername, String customerName, String assigneeUsername,
                                String assigneeName, LocalDateTime createdAt, LocalDateTime updatedAt,
                                LocalDateTime resolvedAt, LocalDateTime closedAt, String resolution, Integer rating,
                                String feedback, long ageDays, List<String> allowedNext, boolean canRate) {

    public static String numberOf(Long id) {
        return "CMP-" + (2000 + id);
    }

    public static ComplaintResponse from(Complaint c, LocalDateTime now) {
        long age = c.getStatus().isActive() ? Duration.between(c.getCreatedAt(), now).toDays() : 0;
        boolean finished = c.getStatus() == ComplaintStatus.RESOLVED || c.getStatus() == ComplaintStatus.CLOSED;
        return new ComplaintResponse(c.getId(), numberOf(c.getId()), c.getSubject(), c.getDescription(), c.getCategory().name(),
                c.getPriority().name(), c.getStatus().name(), c.getCustomer().getUsername(), c.getCustomer().getFullname(),
                c.getAssignee() == null ? null : c.getAssignee().getUsername(),
                c.getAssignee() == null ? null : c.getAssignee().getFullname(), c.getCreatedAt(), c.getUpdatedAt(),
                c.getResolvedAt(), c.getClosedAt(), c.getResolution(), c.getRating(), c.getFeedback(), age,
                c.getStatus().next().stream().map(ComplaintStatus::name).toList(), finished && c.getRating() == null);
    }
}
