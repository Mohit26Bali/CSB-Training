package com.cartnest.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cartnest.entity.CustomerOrder;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

    Page<CustomerOrder> findByUserId(Long userId, Pageable pageable);

    Optional<CustomerOrder> findByOrderNumberAndUserId(String orderNumber, Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<CustomerOrder> findAll(org.springframework.data.jpa.domain.Specification<CustomerOrder> spec, Pageable pageable);

    /** Row lock so a cancel and a status change (e.g. shipping) cannot both go through. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.orderNumber = :number")
    Optional<CustomerOrder> lockByOrderNumber(@Param("number") String orderNumber);

    // ---- dashboard ----

    /** Revenue counts every order that was not cancelled. */
    @Query("select coalesce(sum(o.grandTotal), 0) from CustomerOrder o "
            + "where o.status <> com.cartnest.entity.OrderStatus.CANCELLED")
    BigDecimal totalRevenue();

    @Query("select count(o) from CustomerOrder o where o.status <> com.cartnest.entity.OrderStatus.CANCELLED")
    long countNotCancelled();

    @Query("select new com.cartnest.repository.StatusCount(o.status, count(o)) from CustomerOrder o group by o.status")
    List<StatusCount> countByStatus();

    @Query("select o from CustomerOrder o where o.placedAt >= :from and o.status <> com.cartnest.entity.OrderStatus.CANCELLED")
    List<CustomerOrder> findNotCancelledSince(@Param("from") LocalDateTime from);

    @EntityGraph(attributePaths = "user")
    List<CustomerOrder> findTop8ByOrderByPlacedAtDescIdDesc();

    /** Orders and money spent per customer, for the given customers only. */
    @Query("select new com.cartnest.repository.CustomerStats(o.user.id, count(o), sum(o.grandTotal)) "
            + "from CustomerOrder o where o.status <> com.cartnest.entity.OrderStatus.CANCELLED "
            + "and o.user.id in :userIds group by o.user.id")
    List<CustomerStats> statsForUsers(@Param("userIds") Collection<Long> userIds);
}
