package com.hackathon.referral.dto;

import java.util.List;

public record RecruiterDashboard(long openJobs, long totalReferrals, long hires, List<StatusCount> byStatus,
                                 List<JobStats> byJob) {
}
