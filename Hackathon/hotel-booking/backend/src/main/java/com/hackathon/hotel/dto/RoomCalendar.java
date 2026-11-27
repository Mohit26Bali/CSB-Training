package com.hackathon.hotel.dto;

import java.util.List;

public record RoomCalendar(Long roomId, String number, String type, boolean active, List<Span> bookings) {
}
