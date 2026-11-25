package com.cartnest.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cartnest.entity.ProductVariant;

import jakarta.persistence.LockModeType;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findWithProductById(Long id);

    boolean existsBySkuIgnoreCase(String sku);

    /** Variants of active products that are running out, lowest stock first. */
    @EntityGraph(attributePaths = "product")
    @Query("select v from ProductVariant v where v.product.active = true and v.stock <= :threshold "
            + "order by v.stock, v.id")
    List<ProductVariant> findLowStock(@Param("threshold") int threshold, org.springframework.data.domain.Pageable pageable);

    /**
     * SELECT ... FOR UPDATE on the variants being bought or restocked: a second checkout for the same variant waits
     * until the first one commits and then sees the reduced stock. Always locked in id order, so two orders that share
     * variants cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v join fetch v.product where v.id in :ids order by v.id")
    List<ProductVariant> lockAllById(@Param("ids") Collection<Long> ids);
}
