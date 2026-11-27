package com.hackathon.parking.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.parking.entity.ParkingSlot;

import jakarta.persistence.LockModeType;

public interface SlotRepository extends JpaRepository<ParkingSlot, Long> {

    boolean existsByCode(String code);

    List<ParkingSlot> findAllByOrderByFloorAscCodeAsc();

    /** Row lock so a slot can only be handed to one person. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSlot s where s.id = :id")
    Optional<ParkingSlot> findByIdForUpdate(@Param("id") Long id);
}
