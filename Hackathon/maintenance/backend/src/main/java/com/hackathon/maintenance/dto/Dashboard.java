package com.hackathon.maintenance.dto;

import java.util.List;

public record Dashboard(long open, long assigned, long inProgress, long resolved, long closed, long fresh, long aging, long stale,
                        long emergencies, double averageResolutionHours, List<CategoryStats> byCategory,
                        List<TechnicianResponse> technicianLoad) {
}
