package com.hackathon.hotel.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.hotel.entity.Room;

import jakarta.persistence.LockModeType;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByNumber(String number);

    List<Room> findAllByOrderByFloorAscNumberAsc();

    /** Row lock: bookings, confirmations and cancellations for one room are handled one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
