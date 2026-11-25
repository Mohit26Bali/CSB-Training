package com.cartnest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cartnest.entity.Product;
import com.cartnest.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = "user")
    Page<Review> findByProduct(Product product, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);

    /** Average rating and number of reviews of one product. */
    @Query("select avg(r.rating) from Review r where r.product.id = :productId")
    Double averageRating(@Param("productId") Long productId);

    long countByProductId(Long productId);

    /** How many reviews gave each star rating: rows of (rating, count). */
    @Query("select new com.cartnest.repository.IdCount(cast(r.rating as Long), count(r)) "
            + "from Review r where r.product = :product group by r.rating")
    List<IdCount> countByRating(@Param("product") Product product);
}
