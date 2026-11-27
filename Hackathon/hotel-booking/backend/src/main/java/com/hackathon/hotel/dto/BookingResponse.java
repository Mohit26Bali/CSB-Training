package com.hackathon.hotel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.hotel.entity.Booking;
import com.hackathon.hotel.entity.BookingStatus;

/**
 * number is the readable reference (HB-4012). phase is UPCOMING, IN_STAY or PAST for pending and confirmed bookings (null once
 * cancelled). canCancel tells the customer's screen whether the cancel button makes sense. autoCancelled is only set when
 * confirming: how many other pending requests for the same dates were released.
 */
public record BookingResponse(Long id, String number, Long roomId, String roomNumber, String roomType, LocalDate checkIn, LocalDate checkOut,
                              int nights, int guests, BigDecimal totalPrice, String specialRequests, String status, String phase,
                              String customerUsername, String customerName, LocalDateTime createdAt, LocalDateTime confirmedAt,
                              String confirmedBy, LocalDateTime cancelledAt, String cancelledBy, String cancelReason,
                              boolean canCancel, int autoCancelled) {

    public static String numberOf(Long id) {
        return "HB-" + (4000 + id);
    }

    public static String phaseOf(Booking b, LocalDate today) {
        if (b.getStatus() == BookingStatus.CANCELLED) {
            return null;
        }
        return !b.getCheckOut().isAfter(today) ? "PAST" : !b.getCheckIn().isAfter(today) ? "IN_STAY" : "UPCOMING";
    }

    public static BookingResponse from(Booking b, LocalDate today, int autoCancelled) {
        boolean live = b.getStatus() != BookingStatus.CANCELLED;
        return new BookingResponse(b.getId(), numberOf(b.getId()), b.getRoom().getId(), b.getRoom().getNumber(), b.getRoom().getType().name(),
                b.getCheckIn(), b.getCheckOut(), b.getNights(), b.getGuests(), b.getTotalPrice(), b.getSpecialRequests(), b.getStatus().name(),
                phaseOf(b, today), b.getCustomer().getUsername(), b.getCustomer().getFullname(), b.getCreatedAt(), b.getConfirmedAt(),
                b.getConfirmedBy(), b.getCancelledAt(), b.getCancelledBy(), b.getCancelReason(), live && b.getCheckIn().isAfter(today), autoCancelled);
    }
}
