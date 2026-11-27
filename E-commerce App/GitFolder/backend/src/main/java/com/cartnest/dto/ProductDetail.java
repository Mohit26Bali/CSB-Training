package com.cartnest.dto;

import java.math.BigDecimal;
import java.util.List;

import com.cartnest.entity.VariantType;

public record ProductDetail(Long id, String slug, String title, String description, BrandRef brand,
        List<CategoryRef> breadcrumb, VariantType variantType, BigDecimal price, BigDecimal mrp,
        int discountPercent, boolean inStock, List<String> images, List<VariantResponse> variants,
        List<SpecResponse> specs, String warranty, String shippingInfo, String returnPolicy,
        List<String> tags, RatingSummary ratingSummary, List<ReviewResponse> topReviews,
        List<ProductCard> similar) {
}
