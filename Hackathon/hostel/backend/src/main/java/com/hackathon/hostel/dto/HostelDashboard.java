package com.hackathon.hostel.dto;

import java.util.List;

public record HostelDashboard(int rooms, int beds, int occupied, int free, int occupancyPercent, long pendingRequests,
                              int fullRooms, List<TypeOccupancy> byType) {
}
