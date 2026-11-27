package com.hackathon.appointment.dto;

import java.util.List;

public record ProviderDashboard(long todayAppointments, long pending, long upcomingConfirmed, int freeSlotsWeek, int bookedSlotsWeek, int utilisationPercent,
                                List<DayCount> days, List<AppointmentResponse> next) {
}
