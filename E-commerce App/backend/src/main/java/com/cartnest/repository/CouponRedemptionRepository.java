package com.cartnest.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cartnest.entity.CouponRedemption;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    long countByCouponIdAndUserId(Long couponId, Long userId);

    Optional<CouponRedemption> findByOrderId(Long orderId);
}
