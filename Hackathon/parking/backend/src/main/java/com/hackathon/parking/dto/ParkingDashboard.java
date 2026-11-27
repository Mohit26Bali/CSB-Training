package com.hackathon.parking.dto;

import java.util.List;

public record ParkingDashboard(int slots, int occupied, int free, int reserved, int maintenance, int occupancyPercent,
                               long pendingRequests, List<FloorStats> byFloor) {
}
