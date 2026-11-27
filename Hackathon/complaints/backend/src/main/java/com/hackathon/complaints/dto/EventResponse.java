package com.hackathon.complaints.dto;

import java.time.LocalDateTime;

import com.hackathon.complaints.entity.ComplaintEvent;

public record EventResponse(Long id, String type, String status, String note, String actorName, String actorRole,
                            LocalDateTime createdAt) {

    public static EventResponse from(ComplaintEvent e) {
        return new EventResponse(e.getId(), e.getType().name(), e.getStatus() == null ? null : e.getStatus().name(), e.getNote(),
                e.getActor().getFullname(), e.getActor().getRole().name(), e.getCreatedAt());
    }
}
