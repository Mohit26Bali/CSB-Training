package com.hackathon.event.dto;

import java.time.LocalDateTime;

public record EventStats(Long id, String title, String category, LocalDateTime startsAt, String status, int capacity,
                         long confirmed, long waitlisted, int fillPercent) {
}
