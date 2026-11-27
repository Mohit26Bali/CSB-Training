package com.cartnest.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cartnest.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByVariantId(Long variantId);

    @Query("select count(i) > 0 from OrderItem i where i.order.user.id = :userId and i.product.id = :productId "
            + "and i.order.status = com.cartnest.entity.OrderStatus.DELIVERED")
    boolean wasDelivered(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("select new com.cartnest.repository.ProductSales(i.product.id, sum(i.quantity), sum(i.lineTotal)) "
            + "from OrderItem i where i.order.status <> com.cartnest.entity.OrderStatus.CANCELLED "
            + "group by i.product.id order by sum(i.quantity) desc, sum(i.lineTotal) desc")
    List<ProductSales> bestSellers(Pageable pageable);
}
