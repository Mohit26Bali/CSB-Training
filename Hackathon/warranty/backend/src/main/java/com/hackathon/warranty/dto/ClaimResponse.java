package com.hackathon.warranty.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.warranty.entity.Claim;

public record ClaimResponse(Long id, Long productId, String productName, String brand, String category,
                            String categoryLabel, String serialNumber, LocalDate warrantyEnd, String customerUsername,
                            String customerName, String issueDescription, String priority, String status,
                            LocalDateTime createdAt, String decidedBy, LocalDateTime decidedAt,
                            String rejectionReason, LocalDateTime repairStartedAt, LocalDateTime completedAt,
                            String resolutionNote) {

    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(c.getId(), c.getProduct().getId(), c.getProduct().getName(), c.getProduct().getBrand(),
                c.getProduct().getCategory().name(), c.getProduct().getCategory().getLabel(),
                c.getProduct().getSerialNumber(), c.getProduct().getWarrantyEnd(), c.getCustomer().getUsername(),
                c.getCustomer().getFullname(), c.getIssueDescription(), c.getPriority().name(), c.getStatus().name(),
                c.getCreatedAt(), c.getDecidedBy(), c.getDecidedAt(), c.getRejectionReason(), c.getRepairStartedAt(),
                c.getCompletedAt(), c.getResolutionNote());
    }
}
