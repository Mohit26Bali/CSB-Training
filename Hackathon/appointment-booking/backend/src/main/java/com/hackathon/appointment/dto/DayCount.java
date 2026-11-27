package com.hackathon.appointment.dto;

import java.time.LocalDate;

public record DayCount(LocalDate date, int slots, int booked) {
}
