package com.hackathon.food.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.food.entity.FoodOrder;
import com.hackathon.food.entity.OrderStatus;
import com.hackathon.food.entity.User;

import jakarta.persistence.LockModeType;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {

    /** Plain values, so the order is not loaded before its row is locked. */
    interface Peek {
        String getCustomerUsername();
    }

    /** Orders per status, for the sales page. */
    interface StatusTotal {
        OrderStatus getStatus();

        long getTotal();
    }

    /** Units sold per dish name, for the best-seller list. */
    interface ItemTotal {
        String getName();

        long getQuantity();
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

    /** A retried "place order" with the same key finds the order the first attempt made. */
    @EntityGraph(attributePaths = { "customer", "items" })
    Optional<FoodOrder> findByCustomerAndIdempotencyKey(User customer, String idempotencyKey);

    /** The orders with these ids, with their customer and lines loaded in one query. The caller restores the order. */
    @EntityGraph(attributePaths = { "customer", "items" })
    List<FoodOrder> findByIdIn(Collection<Long> ids);

    // ---------- the order board (ids first, so paging happens in SQL and not on joined rows) ----------

    /**
     * Ids of orders in the given statuses that match the search, open orders first and newest first. The search is a lower-case
     * "%text%" pattern matched against the customer's name and username, the phone number and the order number (FD-1042).
     */
    @Query("""
            select o.id from FoodOrder o join o.customer c
            where o.status in :statuses
              and (lower(c.fullname) like :q or lower(c.username) like :q or o.phone like :q
                   or lower(concat('fd-', cast(o.id + 1000 as string))) like :q)
            order by case when o.status in :openStatuses then 0 else 1 end, o.id desc
            """)
    List<Long> boardIds(@Param("statuses") Collection<OrderStatus> statuses, @Param("openStatuses") Collection<OrderStatus> openStatuses,
                        @Param("q") String q, Pageable pageable);

    /** Ids of one customer's orders in the given statuses, newest first. */
    @Query("select o.id from FoodOrder o where o.customer = :customer and o.status in :statuses order by o.id desc")
    List<Long> customerOrderIds(@Param("customer") User customer, @Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    // ---------- the sales page: aggregates in SQL, only the last week's orders are read row by row ----------

    @Query("select o.status as status, count(o) as total from FoodOrder o group by o.status")
    List<StatusTotal> countByStatus();

    @Query("select count(o) from FoodOrder o where o.status <> :excluded")
    long countExcluding(@Param("excluded") OrderStatus excluded);

    @Query("select coalesce(sum(o.total), 0) from FoodOrder o where o.status <> :excluded")
    java.math.BigDecimal sumTotalExcluding(@Param("excluded") OrderStatus excluded);

    @Query("""
            select i.itemName as name, sum(i.quantity) as quantity from OrderItem i
            where i.order.status <> :excluded
            group by i.itemName
            order by sum(i.quantity) desc, i.itemName asc
            """)
    List<ItemTotal> bestSellers(@Param("excluded") OrderStatus excluded, Pageable pageable);

    List<FoodOrder> findByStatusNotAndCreatedAtGreaterThanEqual(OrderStatus excluded, LocalDateTime from);

    long countByStatusAndCreatedAtGreaterThanEqual(OrderStatus status, LocalDateTime from);
}
