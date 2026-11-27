package com.hackathon.event.dto;

import java.util.List;

public record EventDashboard(long totalEvents, long upcomingPublished, long confirmedSeats, long waitlisted,
                             List<EventStats> events) {
}
