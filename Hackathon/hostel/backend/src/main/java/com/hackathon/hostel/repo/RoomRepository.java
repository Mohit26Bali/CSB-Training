package com.hackathon.hostel.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.hostel.entity.Room;

import jakarta.persistence.LockModeType;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByNumber(String number);

    Optional<Room> findByNumber(String number);

    List<Room> findAllByOrderByFloorAscNumberAsc();

    /** Row lock so two admins cannot put students into the last bed at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
