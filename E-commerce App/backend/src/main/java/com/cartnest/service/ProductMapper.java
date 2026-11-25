package com.cartnest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.cartnest.dto.ProductCard;
import com.cartnest.dto.ReviewResponse;
import com.cartnest.dto.VariantResponse;
import com.cartnest.entity.Product;
import com.cartnest.entity.ProductVariant;
import com.cartnest.entity.Review;

/** Entity to DTO conversions shared by the catalogue services. */
@Component
public class ProductMapper {

    public ProductCard toCard(Product p) {
        return new ProductCard(p.getId(), p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getThumbnailUrl(), p.getPrice(), p.getMrp(), p.getDiscountPercent(), p.getRatingAvg(),
                p.getRatingCount(), p.isInStock());
    }

    public VariantResponse toVariant(ProductVariant v) {
        return new VariantResponse(v.getId(), v.getSku(), v.getLabel(), v.getPrice(), v.getMrp(),
                discountPercent(v.getPrice(), v.getMrp()), v.getStock());
    }

    public ReviewResponse toReview(Review r) {
        return new ReviewResponse(r.getId(), r.getUser().getFullName(), r.getRating(), r.getComment(),
                r.getCreatedAt());
    }

    /** Whole percent saved against the MRP, rounded down: MRP 949, price 849 is 10%. */
    public static int discountPercent(BigDecimal price, BigDecimal mrp) {
        if (mrp.signum() <= 0 || price.compareTo(mrp) >= 0) {
            return 0;
        }
        return mrp.subtract(price).multiply(BigDecimal.valueOf(100)).divide(mrp, 0, RoundingMode.FLOOR).intValue();
    }
}
