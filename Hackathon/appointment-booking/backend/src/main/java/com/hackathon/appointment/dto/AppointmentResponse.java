package com.hackathon.appointment.dto;

import java.time.LocalDateTime;

import com.hackathon.appointment.entity.Appointment;
import com.hackathon.appointment.entity.AppointmentStatus;

public record AppointmentResponse(Long id, String number, Long slotId, Long providerId, String providerName, String providerTitle, String location,
                                  String customerName, String customerUsername, LocalDateTime start, LocalDateTime end, long minutes, String status,
                                  String reason, LocalDateTime createdAt, LocalDateTime confirmedAt, String confirmedBy, LocalDateTime cancelledAt,
                                  String cancelledBy, String cancelReason, String phase, boolean canCancel, boolean canConfirm) {

    /** Customers may cancel up to this long before the start. */
    public static final long CANCEL_NOTICE_HOURS = 2;

    public static AppointmentResponse of(Appointment a, LocalDateTime now) {
        var s = a.getSlot();
        var p = s.getProvider();
        boolean live = a.getStatus() != AppointmentStatus.CANCELLED;
        String phase = s.getEndTime().isBefore(now) ? "PAST" : s.getStartTime().isBefore(now) ? "NOW" : "UPCOMING";
        return new AppointmentResponse(a.getId(), "AP-" + (3000 + a.getId()), s.getId(), p.getId(), p.getUser().getFullname(), p.getTitle(), p.getLocation(),
                a.getCustomer().getFullname(), a.getCustomer().getUsername(), s.getStartTime(), s.getEndTime(), s.minutes(), a.getStatus().name(),
                a.getReason(), a.getCreatedAt(), a.getConfirmedAt(), a.getConfirmedBy(), a.getCancelledAt(), a.getCancelledBy(), a.getCancelReason(), phase,
                live && s.getStartTime().isAfter(now.plusHours(CANCEL_NOTICE_HOURS)),
                a.getStatus() == AppointmentStatus.PENDING && s.getStartTime().isAfter(now));
    }
}
