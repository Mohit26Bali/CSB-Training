package com.hackathon.parking.dto;

import java.time.LocalDateTime;

import com.hackathon.parking.entity.ParkingAllocation;

public record AllocationResponse(Long id, Long slotId, String slotCode, int floor, String employeeUsername,
                                 String employeeName, String vehicleNumber, LocalDateTime allocatedAt,
                                 String allocatedBy, LocalDateTime releasedAt, String releasedBy, boolean active) {

    public static AllocationResponse from(ParkingAllocation a) {
        return new AllocationResponse(a.getId(), a.getSlot().getId(), a.getSlot().getCode(), a.getSlot().getFloor(),
                a.getEmployee().getUsername(), a.getEmployee().getFullname(), a.getVehicleNumber(), a.getAllocatedAt(),
                a.getAllocatedBy(), a.getReleasedAt(), a.getReleasedBy(), a.isActive());
    }
}
