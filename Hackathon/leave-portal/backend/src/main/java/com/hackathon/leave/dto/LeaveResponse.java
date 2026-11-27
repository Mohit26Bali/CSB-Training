package com.hackathon.leave.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.leave.entity.LeaveRequest;

public record LeaveResponse(Long id, String employeeUsername, String employeeName, String leaveType,
                            String leaveTypeLabel, LocalDate startDate, LocalDate endDate, int days, String reason,
                            String status, LocalDateTime submittedAt, String decidedBy, LocalDateTime decidedAt,
                            String decisionNote) {

    public static LeaveResponse from(LeaveRequest r) {
        return new LeaveResponse(r.getId(), r.getEmployee().getUsername(), r.getEmployee().getFullname(),
                r.getLeaveType().name(), r.getLeaveType().getLabel(), r.getStartDate(), r.getEndDate(), r.getDays(),
                r.getReason(), r.getStatus().name(), r.getSubmittedAt(), r.getDecidedBy(), r.getDecidedAt(),
                r.getDecisionNote());
    }
}
