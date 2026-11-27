package com.hackathon.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hackathon.procurement.entity.PurchaseRequest;

public record PurchaseResponse(Long id, String employeeUsername, String employeeName, String itemName, String category,
                               String categoryLabel, int quantity, BigDecimal estimatedUnitCost,
                               BigDecimal estimatedTotal, String justification, String status,
                               LocalDateTime submittedAt, String decidedBy, LocalDateTime decidedAt,
                               String rejectionReason, String orderReference, LocalDateTime orderedAt,
                               LocalDateTime deliveredAt) {

    public static PurchaseResponse from(PurchaseRequest r) {
        return new PurchaseResponse(r.getId(), r.getEmployee().getUsername(), r.getEmployee().getFullname(),
                r.getItemName(), r.getCategory().name(), r.getCategory().getLabel(), r.getQuantity(),
                r.getEstimatedUnitCost(), r.getEstimatedTotal(), r.getJustification(), r.getStatus().name(),
                r.getSubmittedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getRejectionReason(),
                r.getOrderReference(), r.getOrderedAt(), r.getDeliveredAt());
    }
}
