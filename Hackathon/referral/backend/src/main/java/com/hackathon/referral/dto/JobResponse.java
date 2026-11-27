package com.hackathon.referral.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.hackathon.referral.entity.Job;

/** referralCount is only filled in for recruiters (0 for employees). */
public record JobResponse(Long id, String title, String department, String location, String description,
                          List<String> skills, String status, String createdBy, LocalDateTime createdAt,
                          LocalDateTime publishedAt, long referralCount) {

    public static JobResponse from(Job j, long referralCount) {
        return new JobResponse(j.getId(), j.getTitle(), j.getDepartment(), j.getLocation(), j.getDescription(),
                j.getSkillList(), j.getStatus().name(), j.getCreatedBy(), j.getCreatedAt(), j.getPublishedAt(),
                referralCount);
    }
}
