package com.icc.dashboard.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Incidents created and resolved per day, oldest first. */
public record TrendsResponse(@NotNull int days, @NotNull List<DailyCount> daily) {

    public record DailyCount(@NotNull LocalDate date, @NotNull long created, @NotNull long resolved) {
    }
}
