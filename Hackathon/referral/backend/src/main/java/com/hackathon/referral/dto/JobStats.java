package com.hackathon.referral.dto;

public record JobStats(Long jobId, String title, String department, String jobStatus, long total, long inProgress,
                       long selected, long rejected) {
}
