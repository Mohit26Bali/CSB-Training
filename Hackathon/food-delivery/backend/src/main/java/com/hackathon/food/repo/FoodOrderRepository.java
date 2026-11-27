package com.hackathon.food.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.food.entity.FoodOrder;
import com.hackathon.food.entity.User;

import jakarta.persistence.LockModeType;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {

    /** Plain values, so the order is not loaded before its row is locked. */
    interface Peek {
        String getCustomerUsername();
    }

    @Query("select o.customer.username as customerUsername from FoodOrder o where o.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    /** Row lock: status changes and cancellations for one order are handled one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from FoodOrder o where o.id = :id")
    Optional<FoodOrder> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "customer", "items" })
    Optional<FoodOrder> findDetailedById(Long id);

    @EntityGraph(attributePaths = { "customer", "items" })
    List<FoodOrder> findByCustomerOrderByIdDesc(User customer);

    @EntityGraph(attributePaths = { "customer", "items" })
    Optional<FoodOrder> findTopByCustomerOrderByIdDesc(User customer);

    @EntityGraph(attributePaths = { "customer", "items" })
    List<FoodOrder> findAllByOrderByIdDesc();
}
