package com.hackathon.vehicle.dto;

import java.time.LocalDate;

import com.hackathon.vehicle.entity.Vehicle;

public record VehicleResponse(Long id, String number, String type, String make, String model, int year, String ownerName, String ownerUsername,
                              int completedServices, LocalDate nextService) {

    public static VehicleResponse of(Vehicle v, int completed, LocalDate next) {
        return new VehicleResponse(v.getId(), v.getNumber(), v.getType().name(), v.getMake(), v.getModel(), v.getYear(),
                v.getOwner().getFullname(), v.getOwner().getUsername(), completed, next);
    }
}
