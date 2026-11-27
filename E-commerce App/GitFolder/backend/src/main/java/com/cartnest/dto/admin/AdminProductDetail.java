package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.util.List;

import com.cartnest.dto.SpecResponse;
import com.cartnest.entity.VariantType;

public record AdminProductDetail(Long id, String slug, String title, String description, Long categoryId, String categoryName,
        Long brandId, String brandName, VariantType variantType, List<String> images, List<AdminVariant> variants,
        List<SpecResponse> specs, List<String> tags, String warranty, String shippingInfo, String returnPolicy,
        boolean active, BigDecimal price, BigDecimal mrp, int discountPercent) {
}
