package com.hackathon.parking.dto;

import java.time.LocalDateTime;

import com.hackathon.parking.entity.ParkingRequest;

/** queuePosition is the 1-based place in line, set only while the request is PENDING. */
public record RequestResponse(Long id, String employeeUsername, String employeeName, String vehicleNumber,
                              Integer preferredFloor, String note, String status, Integer queuePosition,
                              LocalDateTime createdAt, String decidedBy, LocalDateTime decidedAt, String decisionReason,
                              String slotCode) {

    public static RequestResponse from(ParkingRequest r, Integer queuePosition) {
        return new RequestResponse(r.getId(), r.getEmployee().getUsername(), r.getEmployee().getFullname(),
                r.getVehicleNumber(), r.getPreferredFloor(), r.getNote(), r.getStatus().name(), queuePosition,
                r.getCreatedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionReason(),
                r.getSlot() == null ? null : r.getSlot().getCode());
    }
}
