package com.cartnest.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.admin.AdminCouponResponse;
import com.cartnest.dto.admin.CouponRequest;
import com.cartnest.entity.Coupon;
import com.cartnest.entity.DiscountType;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.CouponRepository;

@Service
@Transactional
public class AdminCouponService {
    private final CouponRepository couponRepository;

    public AdminCouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminCouponResponse> list() {
        return couponRepository.findAll(Sort.by("createdAt").descending().and(Sort.by("id").descending())).stream()
                .map(AdminCouponService::toResponse)
                .toList();
    }

    public AdminCouponResponse create(CouponRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Coupon " + code + " already exists");
        }
        Coupon coupon = new Coupon(code);
        apply(coupon, request);
        return toResponse(couponRepository.save(coupon));
    }

    public AdminCouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = find(id);
        if (!coupon.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BadRequestException("A coupon's code cannot be changed; create a new coupon instead");
        }
        apply(coupon, request);
        return toResponse(coupon);
    }

    public AdminCouponResponse setActive(Long id, boolean active) {
        Coupon coupon = find(id);
        coupon.setActive(active);
        return toResponse(coupon);
    }

    private static void apply(Coupon c, CouponRequest r) {
        if (r.discountType() == DiscountType.PERCENT && r.discountValue().compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BadRequestException("A percentage discount can be at most 90%");
        }
        if (!r.validUntil().isAfter(r.validFrom())) {
            throw new BadRequestException("validUntil must be after validFrom");
        }
        if (r.usageLimit() != null && r.usageLimit() < c.getUsedCount()) {
            throw new BadRequestException("usageLimit cannot be below the " + c.getUsedCount() + " uses so far");
        }
        c.setDescription(r.description().trim());
        c.setDiscountType(r.discountType());
        c.setDiscountValue(r.discountValue());

        c.setMaxDiscount(r.discountType() == DiscountType.PERCENT ? r.maxDiscount() : null);
        c.setMinOrderAmount(r.minOrderAmount());
        c.setValidFrom(r.validFrom());
        c.setValidUntil(r.validUntil());
        c.setUsageLimit(r.usageLimit());
        c.setPerUserLimit(r.perUserLimit());
        if (r.active() != null) {
            c.setActive(r.active());
        }
    }

    private Coupon find(Long id) {
        return couponRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Coupon " + id + " not found"));
    }

    static String state(Coupon c) {
        LocalDateTime now = LocalDateTime.now();
        if (!c.isActive()) {
            return "DISABLED";
        }
        if (now.isBefore(c.getValidFrom())) {
            return "SCHEDULED";
        }
        if (now.isAfter(c.getValidUntil())) {
            return "EXPIRED";
        }
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) {
            return "USED_UP";
        }
        return "ACTIVE";
    }

    private static AdminCouponResponse toResponse(Coupon c) {
        return new AdminCouponResponse(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                c.getDiscountValue(), c.getMaxDiscount(), c.getMinOrderAmount(), c.getValidFrom(), c.getValidUntil(),
                c.getUsageLimit(), c.getPerUserLimit(), c.getUsedCount(), c.isActive(), state(c));
    }
}
