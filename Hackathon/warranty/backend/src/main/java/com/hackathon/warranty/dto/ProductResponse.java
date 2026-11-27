package com.hackathon.warranty.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.hackathon.warranty.entity.Product;

/** daysLeft is negative once the warranty has expired. expiringSoon means 30 days or fewer remain. */
public record ProductResponse(Long id, String name, String brand, String category, String categoryLabel,
                              String serialNumber, LocalDate purchaseDate, int warrantyMonths, LocalDate warrantyEnd,
                              boolean warrantyActive, long daysLeft, boolean expiringSoon, boolean hasActiveClaim) {

    private static final int SOON_DAYS = 30;

    public static ProductResponse from(Product p, LocalDate today, boolean hasActiveClaim) {
        long daysLeft = ChronoUnit.DAYS.between(today, p.getWarrantyEnd());
        boolean active = p.isWarrantyActive(today);
        return new ProductResponse(p.getId(), p.getName(), p.getBrand(), p.getCategory().name(),
                p.getCategory().getLabel(), p.getSerialNumber(), p.getPurchaseDate(), p.getWarrantyMonths(),
                p.getWarrantyEnd(), active, daysLeft, active && daysLeft <= SOON_DAYS, hasActiveClaim);
    }
}
