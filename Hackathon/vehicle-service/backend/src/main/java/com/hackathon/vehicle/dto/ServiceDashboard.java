package com.hackathon.vehicle.dto;

import java.math.BigDecimal;
import java.util.List;

public record ServiceDashboard(int bays, int baysUsedToday, long arrivalsToday, long inServiceNow, long upcomingBooked, long completedThisMonth,
                               BigDecimal revenueThisMonth, List<ServiceCount> topServices, List<DayLoad> load) {
}
