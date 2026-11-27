package com.cartnest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cartnest.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @EntityGraph(attributePaths = { "variant", "variant.product", "variant.product.brand" })
    List<CartItem> findByUserIdOrderByAddedAtDescIdDesc(Long userId);

    Optional<CartItem> findByUserIdAndVariantId(Long userId, Long variantId);

    @EntityGraph(attributePaths = { "variant", "variant.product" })
    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    @Query("select new com.cartnest.repository.CartLine(c.variant.id, c.quantity) from CartItem c "
            + "where c.user.id = :userId order by c.id")
    List<CartLine> findLines(@Param("userId") Long userId);

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}
