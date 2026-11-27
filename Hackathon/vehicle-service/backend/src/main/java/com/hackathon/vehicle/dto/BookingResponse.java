package com.hackathon.vehicle.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.vehicle.entity.BookingStatus;
import com.hackathon.vehicle.entity.ServiceBooking;

public record BookingResponse(Long id, String number, Long vehicleId, String vehicleNumber, String vehicleLabel, String vehicleType,
                              String customerName, String customerUsername, Long serviceTypeId, String serviceName,
                              LocalDate startDate, LocalDate endDate, int days, String status, BigDecimal estimatedCost, BigDecimal finalCost,
                              String notes, String workNotes, LocalDateTime createdAt, LocalDateTime startedAt, String startedBy,
                              LocalDateTime completedAt, String completedBy, LocalDateTime cancelledAt, String cancelledBy, String cancelReason,
                              boolean canCancel, boolean canStart) {

    public static BookingResponse of(ServiceBooking b, LocalDate today) {
        boolean booked = b.getStatus() == BookingStatus.BOOKED;
        return new BookingResponse(b.getId(), "VS-" + (5000 + b.getId()), b.getVehicle().getId(), b.getVehicle().getNumber(),
                b.getVehicle().getMake() + " " + b.getVehicle().getModel(), b.getVehicle().getType().name(),
                b.getCustomer().getFullname(), b.getCustomer().getUsername(), b.getServiceType().getId(), b.getServiceType().getName(),
                b.getStartDate(), b.getEndDate(), b.getServiceType().getDurationDays(), b.getStatus().name(), b.getEstimatedCost(), b.getFinalCost(),
                b.getNotes(), b.getWorkNotes(), b.getCreatedAt(), b.getStartedAt(), b.getStartedBy(), b.getCompletedAt(), b.getCompletedBy(),
                b.getCancelledAt(), b.getCancelledBy(), b.getCancelReason(), booked, booked && !b.getStartDate().isAfter(today));
    }
}
