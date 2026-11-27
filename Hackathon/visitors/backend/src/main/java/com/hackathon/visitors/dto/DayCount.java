package com.hackathon.visitors.dto;

import java.time.LocalDate;

public record DayCount(LocalDate date, long expected, long arrived) {
}
