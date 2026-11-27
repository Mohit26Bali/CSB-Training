package com.hackathon.hostel.dto;

import java.time.LocalDateTime;

import com.hackathon.hostel.entity.RoomAllocation;

public record AllocationResponse(Long id, Long roomId, String roomNumber, int floor, String roomType, String studentUsername,
                                 String studentName, LocalDateTime allocatedAt, String allocatedBy, LocalDateTime vacatedAt,
                                 boolean active) {

    public static AllocationResponse from(RoomAllocation a) {
        return new AllocationResponse(a.getId(), a.getRoom().getId(), a.getRoom().getNumber(), a.getRoom().getFloor(),
                a.getRoom().getType().name(), a.getStudent().getUsername(), a.getStudent().getFullname(),
                a.getAllocatedAt(), a.getAllocatedBy(), a.getVacatedAt(), a.isActive());
    }
}
