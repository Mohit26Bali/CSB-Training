package com.hackathon.complaints.dto;

import java.time.LocalDate;

public record DayCount(LocalDate date, long filed, long resolved) {
}
