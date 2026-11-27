package com.hackathon.vehicle.repo;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.vehicle.entity.User;
import com.hackathon.vehicle.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    boolean existsByNumber(String number);

    @EntityGraph(attributePaths = "owner")
    List<Vehicle> findByOwnerOrderByIdDesc(User owner);

    @EntityGraph(attributePaths = "owner")
    List<Vehicle> findAllByOrderByNumberAsc();
}
