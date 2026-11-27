package com.hackathon.hotel.dto;

import java.time.LocalDate;
import java.util.List;

/** One month, room by room: every pending or confirmed stay that touches the month. */
public record CalendarResponse(String month, LocalDate first, int days, List<RoomCalendar> rooms) {
}
