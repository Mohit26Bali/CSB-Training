package com.hackathon.appointment.dto;

import java.time.LocalDateTime;

import com.hackathon.appointment.entity.Appointment;
import com.hackathon.appointment.entity.Slot;

/** status: AVAILABLE, PENDING or CONFIRMED (a live appointment holds it). Customer details are filled only for the provider. */
public record SlotResponse(Long id, Long providerId, String providerName, LocalDateTime start, LocalDateTime end, long minutes, String status,
                           Long appointmentId, String customerName, String reason) {

    public static SlotResponse of(Slot s, Appointment live, boolean forProvider) {
        return new SlotResponse(s.getId(), s.getProvider().getId(), s.getProvider().getUser().getFullname(), s.getStartTime(), s.getEndTime(), s.minutes(),
                live == null ? "AVAILABLE" : live.getStatus().name(),
                forProvider && live != null ? live.getId() : null, forProvider && live != null ? live.getCustomer().getFullname() : null,
                forProvider && live != null ? live.getReason() : null);
    }
}
