package com.hackathon.maintenance.dto;

import com.hackathon.maintenance.entity.Technician;

public record TechnicianResponse(Long id, String name, String trade, String phone, boolean active, long activeJobs) {

    public static TechnicianResponse from(Technician t, long activeJobs) {
        return new TechnicianResponse(t.getId(), t.getName(), t.getTrade().name(), t.getPhone(), t.isActive(), activeJobs);
    }
}
