package com.cartnest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cartnest.entity.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    @EntityGraph(attributePaths = { "product", "product.brand" })
    List<WishlistItem> findByUserIdOrderByAddedAtDescIdDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);
}
