package com.hackathon.hotel.dto;

import java.math.BigDecimal;
import java.util.List;

public record HotelDashboard(int rooms, int activeRooms, int occupiedTonight, int occupancyPercent, int arrivalsToday, int departuresToday,
                             long pendingBookings, BigDecimal revenueThisMonth, List<TypeStats> byType, List<BookingResponse> nextArrivals) {
}
