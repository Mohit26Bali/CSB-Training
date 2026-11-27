package com.hackathon.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hackathon.asset.entity.Asset;
import com.hackathon.asset.entity.Assignment;

public record AssetResponse(Long id, String assetTag, String serialNumber, String name, String category, String brand, String model,
                            LocalDate purchaseDate, BigDecimal purchasePrice, String condition, String status, String notes,
                            Long assignmentId, String holderName, String holderUsername, LocalDateTime heldSince) {

    public static AssetResponse of(Asset a, Assignment active) {
        return new AssetResponse(a.getId(), a.getAssetTag(), a.getSerialNumber(), a.getName(), a.getCategory().name(), a.getBrand(), a.getModel(),
                a.getPurchaseDate(), a.getPurchasePrice(), a.getCondition().name(), a.getStatus().name(), a.getNotes(),
                active == null ? null : active.getId(), active == null ? null : active.getEmployee().getFullname(),
                active == null ? null : active.getEmployee().getUsername(), active == null ? null : active.getAssignedAt());
    }
}
