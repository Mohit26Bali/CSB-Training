package com.hackathon.vehicle.dto;

import java.math.BigDecimal;

import com.hackathon.vehicle.entity.ServiceType;

public record ServiceTypeResponse(Long id, String name, String description, int durationDays, BigDecimal basePrice, BigDecimal estimatedCost, boolean active) {

    public static ServiceTypeResponse of(ServiceType s, BigDecimal estimate) {
        return new ServiceTypeResponse(s.getId(), s.getName(), s.getDescription(), s.getDurationDays(), s.getBasePrice(), estimate, s.isActive());
    }
}
