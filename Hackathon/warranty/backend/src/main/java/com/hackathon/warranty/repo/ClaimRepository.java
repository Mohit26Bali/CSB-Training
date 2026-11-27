package com.hackathon.warranty.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.warranty.entity.Claim;
import com.hackathon.warranty.entity.ClaimStatus;
import com.hackathon.warranty.entity.Product;
import com.hackathon.warranty.entity.User;

import jakarta.persistence.LockModeType;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    boolean existsByProductAndStatusIn(Product product, Collection<ClaimStatus> statuses);

    @EntityGraph(attributePaths = { "product", "customer" })
    List<Claim> findByCustomerOrderByCreatedAtDesc(User customer);

    @EntityGraph(attributePaths = { "product", "customer" })
    List<Claim> findAllByOrderByCreatedAtDesc();

    /** The products of this owner that currently have an open claim. */
    @Query("select c.product.id from Claim c where c.customer = :customer and c.status in :statuses")
    List<Long> findProductIdsWithClaims(@Param("customer") User customer, @Param("statuses") Collection<ClaimStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> findByIdForUpdate(@Param("id") Long id);
}
