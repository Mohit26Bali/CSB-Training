package com.hackathon.food.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.food.entity.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    Optional<MenuItem> findByNameIgnoreCase(String name);

    List<MenuItem> findAllByOrderByCategoryAscNameAsc();
}
