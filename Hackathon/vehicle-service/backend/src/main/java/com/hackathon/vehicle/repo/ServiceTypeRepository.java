package com.hackathon.vehicle.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.vehicle.entity.ServiceType;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<ServiceType> findAllByOrderByNameAsc();
}
