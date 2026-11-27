package com.hackathon.parking.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.parking.entity.ParkingAllocation;
import com.hackathon.parking.entity.ParkingSlot;
import com.hackathon.parking.entity.User;

public interface ParkingAllocationRepository extends JpaRepository<ParkingAllocation, Long> {

    /** The slot id only, so the allocation is not loaded before the slot is locked. */
    @Query("select a.slot.id from ParkingAllocation a where a.id = :id")
    Optional<Long> slotIdOf(@Param("id") Long id);

    boolean existsByEmployeeAndReleasedAtIsNull(User employee);

    boolean existsBySlotAndReleasedAtIsNull(ParkingSlot slot);

    boolean existsByVehicleNumberAndReleasedAtIsNull(String vehicleNumber);

    @EntityGraph(attributePaths = { "slot", "employee" })
    Optional<ParkingAllocation> findByEmployeeAndReleasedAtIsNull(User employee);

    @EntityGraph(attributePaths = { "slot", "employee" })
    List<ParkingAllocation> findAllByOrderByAllocatedAtDesc();

    @EntityGraph(attributePaths = { "slot", "employee" })
    List<ParkingAllocation> findByReleasedAtIsNull();
}
