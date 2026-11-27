package com.hackathon.warranty.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.warranty.entity.Product;
import com.hackathon.warranty.entity.User;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByOwnerOrderByPurchaseDateDesc(User owner);

    boolean existsBySerialNumber(String serialNumber);

    /**
     * Row lock on the product. Filing a claim takes it first, so two claims for the same product at the same moment
     * are handled one after the other and the second sees the first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
