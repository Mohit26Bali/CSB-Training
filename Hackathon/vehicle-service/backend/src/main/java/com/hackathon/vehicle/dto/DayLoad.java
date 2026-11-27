package com.hackathon.vehicle.dto;

import java.time.LocalDate;

public record DayLoad(LocalDate date, int booked, int bays, int free) {
}
