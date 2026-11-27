package com.hackathon.maintenance.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.maintenance.entity.Technician;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<Technician> findAllByOrderByNameAsc();
}
