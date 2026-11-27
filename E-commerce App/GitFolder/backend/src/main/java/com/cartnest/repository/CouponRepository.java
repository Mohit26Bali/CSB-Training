package com.cartnest.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cartnest.entity.Coupon;

import jakarta.persistence.LockModeType;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where upper(c.code) = upper(:code)")
    Optional<Coupon> lockByCode(@Param("code") String code);

    @Query("select c from Coupon c where c.active = true and c.validFrom <= :now and c.validUntil >= :now "
            + "and (c.usageLimit is null or c.usedCount < c.usageLimit) order by c.minOrderAmount")
    List<Coupon> findAvailable(@Param("now") LocalDateTime now);
}
