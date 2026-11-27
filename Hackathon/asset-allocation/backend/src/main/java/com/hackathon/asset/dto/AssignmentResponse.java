package com.hackathon.asset.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.asset.entity.Assignment;
import com.hackathon.asset.entity.AssignmentStatus;

public record AssignmentResponse(Long id, Long assetId, String assetTag, String assetName, String category, String serialNumber,
                                 String employeeName, String employeeUsername, LocalDateTime assignedAt, String assignedBy, LocalDate dueDate,
                                 String conditionOut, String note, String status, LocalDateTime returnRequestedAt, String returnNote,
                                 LocalDateTime returnedAt, String receivedBy, String conditionIn, String receiveNote,
                                 boolean overdue, boolean canRequestReturn) {

    public static AssignmentResponse of(Assignment a, LocalDate today) {
        var asset = a.getAsset();
        boolean overdue = a.isActive() && a.getDueDate() != null && a.getDueDate().isBefore(today);
        return new AssignmentResponse(a.getId(), asset.getId(), asset.getAssetTag(), asset.getName(), asset.getCategory().name(), asset.getSerialNumber(),
                a.getEmployee().getFullname(), a.getEmployee().getUsername(), a.getAssignedAt(), a.getAssignedBy(), a.getDueDate(),
                a.getConditionOut().name(), a.getNote(), a.getStatus().name(), a.getReturnRequestedAt(), a.getReturnNote(),
                a.getReturnedAt(), a.getReceivedBy(), a.getConditionIn() == null ? null : a.getConditionIn().name(), a.getReceiveNote(),
                overdue, a.getStatus() == AssignmentStatus.ASSIGNED);
    }
}
