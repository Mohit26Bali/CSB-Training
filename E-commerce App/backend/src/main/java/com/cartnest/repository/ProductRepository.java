package com.cartnest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cartnest.entity.Product;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Brand and category are fetched in the same query; variants and images follow in batches
    @Override
    @EntityGraph(attributePaths = { "brand", "category" })
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "brand", "category" })
    List<Product> findAll(Specification<Product> spec);

    @EntityGraph(attributePaths = { "brand", "category" })
    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    /** Row lock while the rating is recalculated, so two reviews at once cannot overwrite each other's result. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(@Param("id") Long id);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByBrandId(Long brandId);

    long countByActiveTrue();

    @Query("select new com.cartnest.repository.IdCount(p.category.id, count(p)) "
            + "from Product p where p.active = true group by p.category.id")
    List<IdCount> countActiveByCategory();

    @Query("select new com.cartnest.repository.IdCount(p.brand.id, count(p)) "
            + "from Product p where p.active = true and p.brand is not null group by p.brand.id")
    List<IdCount> countActiveByBrand();
}
