package com.cartnest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.CouponResponse;
import com.cartnest.entity.Coupon;
import com.cartnest.entity.CouponRedemption;
import com.cartnest.entity.CustomerOrder;
import com.cartnest.entity.DiscountType;
import com.cartnest.exception.BadRequestException;
import com.cartnest.repository.CouponRedemptionRepository;
import com.cartnest.repository.CouponRepository;
import com.cartnest.repository.UserRepository;

/**
 * Coupon rules: the coupon must exist and be active, today must be inside its validity period, it must not have
 * reached its total usage limit or this customer's limit, and the items must cost at least its minimum order amount.
 * A cancelled order gives its coupon use back.
 */
@Service
@Transactional
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final UserRepository userRepository;

    public CouponService(CouponRepository couponRepository, CouponRedemptionRepository redemptionRepository,
                         UserRepository userRepository) {
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
        this.userRepository = userRepository;
    }

    /** Coupons this customer can still use (whether the cart is big enough is shown at checkout). */
    @Transactional(readOnly = true)
    public List<CouponResponse> available(Long userId) {
        return couponRepository.findAvailable(LocalDateTime.now()).stream()
                .filter(c -> redemptionRepository.countByCouponIdAndUserId(c.getId(), userId) < c.getPerUserLimit())
                .map(c -> new CouponResponse(c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                        c.getMaxDiscount(), c.getMinOrderAmount(), c.getValidUntil()))
                .toList();
    }

    /**
     * For the checkout preview: the discount, or a BadRequestException saying why the coupon cannot be used. Nothing is
     * locked or saved. The preview catches that exception to show the message, so it must not mark the shared
     * transaction for rollback (noRollbackFor).
     */
    @Transactional(readOnly = true, noRollbackFor = BadRequestException.class)
    public Quote preview(String code, Long userId, BigDecimal itemTotal) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BadRequestException("Coupon '" + code.trim().toUpperCase(Locale.ROOT) + "' does not exist"));
        return new Quote(coupon, check(coupon, userId, itemTotal));
    }

    /** For placing an order: locks the coupon row so its total usage limit cannot be overshot by parallel orders. */
    public Quote lockAndQuote(String code, Long userId, BigDecimal itemTotal) {
        Coupon coupon = couponRepository.lockByCode(code.trim())
                .orElseThrow(() -> new BadRequestException("Coupon '" + code.trim().toUpperCase(Locale.ROOT) + "' does not exist"));
        return new Quote(coupon, check(coupon, userId, itemTotal));
    }

    public void redeem(Coupon coupon, Long userId, CustomerOrder order) {
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        redemptionRepository.save(new CouponRedemption(coupon, userRepository.getReferenceById(userId), order));
    }

    /** A cancelled order frees its coupon use again. */
    public void release(CustomerOrder order) {
        redemptionRepository.findByOrderId(order.getId()).ifPresent(redemption -> {
            Coupon coupon = couponRepository.lockByCode(redemption.getCoupon().getCode()).orElseThrow();
            coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
            redemptionRepository.delete(redemption);
        });
    }

    /** Checks every rule and returns the discount in whole rupees. */
    private BigDecimal check(Coupon c, Long userId, BigDecimal itemTotal) {
        LocalDateTime now = LocalDateTime.now();
        if (!c.isActive()) {
            throw new BadRequestException("Coupon " + c.getCode() + " is no longer available");
        }
        if (now.isBefore(c.getValidFrom())) {
            throw new BadRequestException("Coupon " + c.getCode() + " is not active yet");
        }
        if (now.isAfter(c.getValidUntil())) {
            throw new BadRequestException("Coupon " + c.getCode() + " has expired");
        }
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) {
            throw new BadRequestException("Coupon " + c.getCode() + " has been fully redeemed");
        }
        if (redemptionRepository.countByCouponIdAndUserId(c.getId(), userId) >= c.getPerUserLimit()) {
            throw new BadRequestException("You have already used coupon " + c.getCode()
                    + (c.getPerUserLimit() > 1 ? " " + c.getPerUserLimit() + " times" : ""));
        }
        if (itemTotal.compareTo(c.getMinOrderAmount()) < 0) {
            throw new BadRequestException("Add items worth " + rupees(c.getMinOrderAmount().subtract(itemTotal))
                    + " more to use " + c.getCode());
        }
        BigDecimal discount;
        if (c.getDiscountType() == DiscountType.PERCENT) {
            discount = itemTotal.multiply(c.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
            if (c.getMaxDiscount() != null) {
                discount = discount.min(c.getMaxDiscount());
            }
        } else {
            discount = c.getDiscountValue().min(itemTotal);
        }
        return discount.setScale(2, RoundingMode.FLOOR);
    }

    static String rupees(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    /** A coupon and the discount it gives on the current items. */
    public record Quote(Coupon coupon, BigDecimal discount) {
    }
}
