package com.hackathon.hostel.dto;

import java.time.LocalDateTime;

import com.hackathon.hostel.entity.RoomRequest;

/** queuePosition is the 1-based place on the waiting list, set only while the request is PENDING. */
public record RequestResponse(Long id, String studentUsername, String studentName, String preferredType, String note,
                              String status, Integer queuePosition, LocalDateTime createdAt, String decidedBy,
                              LocalDateTime decidedAt, String decisionReason, String roomNumber) {

    public static RequestResponse from(RoomRequest r, Integer queuePosition) {
        return new RequestResponse(r.getId(), r.getStudent().getUsername(), r.getStudent().getFullname(),
                r.getPreferredType() == null ? null : r.getPreferredType().name(), r.getNote(), r.getStatus().name(),
                queuePosition, r.getCreatedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionReason(),
                r.getRoom() == null ? null : r.getRoom().getNumber());
    }
}
