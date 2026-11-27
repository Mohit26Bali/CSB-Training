package com.hackathon.maintenance.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.hackathon.maintenance.entity.MaintenanceRequest;
import com.hackathon.maintenance.entity.RequestStatus;

/**
 * number is the readable reference (MR-3042). For an active request ageDays counts from when it was raised and aging is
 * FRESH (under 2 days), AGING (2 to 4 days) or STALE (5 days or more); finished requests have no aging.
 */
public record RequestResponse(Long id, String number, String category, String description, String location, String priority,
                              String status, String residentUsername, String residentName, Long technicianId,
                              String technicianName, LocalDateTime createdAt, LocalDateTime assignedAt, LocalDateTime startedAt,
                              LocalDateTime resolvedAt, LocalDateTime closedAt, String resolutionNotes, long ageDays,
                              String aging, List<String> allowedNext) {

    public static String numberOf(Long id) {
        return "MR-" + (3000 + id);
    }

    public static String agingOf(MaintenanceRequest r, LocalDateTime now) {
        if (!r.getStatus().isActive()) {
            return null;
        }
        long days = Duration.between(r.getCreatedAt(), now).toDays();
        return days >= 5 ? "STALE" : days >= 2 ? "AGING" : "FRESH";
    }

    public static RequestResponse from(MaintenanceRequest r, LocalDateTime now) {
        long age = r.getStatus().isActive() ? Duration.between(r.getCreatedAt(), now).toDays() : 0;
        return new RequestResponse(r.getId(), numberOf(r.getId()), r.getCategory().name(), r.getDescription(), r.getLocation(),
                r.getPriority().name(), r.getStatus().name(), r.getResident().getUsername(), r.getResident().getFullname(),
                r.getTechnician() == null ? null : r.getTechnician().getId(),
                r.getTechnician() == null ? null : r.getTechnician().getName(), r.getCreatedAt(), r.getAssignedAt(),
                r.getStartedAt(), r.getResolvedAt(), r.getClosedAt(), r.getResolutionNotes(), age, agingOf(r, now),
                r.getStatus().next().stream().map(RequestStatus::name).toList());
    }
}
