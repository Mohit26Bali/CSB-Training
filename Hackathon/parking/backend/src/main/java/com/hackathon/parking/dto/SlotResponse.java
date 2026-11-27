package com.hackathon.parking.dto;

import com.hackathon.parking.entity.ParkingSlot;

/** occupantName / occupantVehicle are only filled in for the admin. */
public record SlotResponse(Long id, String code, int floor, String status, boolean occupied, String occupantName,
                           String occupantVehicle) {

    public static SlotResponse from(ParkingSlot s, boolean occupied, String occupantName, String occupantVehicle) {
        return new SlotResponse(s.getId(), s.getCode(), s.getFloor(), s.getStatus().name(), occupied, occupantName, occupantVehicle);
    }
}
