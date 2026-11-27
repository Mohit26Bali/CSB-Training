package com.cartnest.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cartnest.entity.Brand;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySlug(String slug);
}
