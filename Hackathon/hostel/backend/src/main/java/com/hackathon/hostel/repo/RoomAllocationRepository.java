package com.hackathon.hostel.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.hostel.entity.Room;
import com.hackathon.hostel.entity.RoomAllocation;
import com.hackathon.hostel.entity.User;

public interface RoomAllocationRepository extends JpaRepository<RoomAllocation, Long> {

    /** The room id only, so the allocation is not loaded before the room is locked. */
    @org.springframework.data.jpa.repository.Query("select a.room.id from RoomAllocation a where a.id = :id")
    Optional<Long> roomIdOf(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByStudentAndVacatedAtIsNull(User student);

    long countByRoomAndVacatedAtIsNull(Room room);

    @EntityGraph(attributePaths = { "room", "student" })
    Optional<RoomAllocation> findByStudentAndVacatedAtIsNull(User student);

    @EntityGraph(attributePaths = { "room", "student" })
    List<RoomAllocation> findAllByOrderByAllocatedAtDesc();

    @EntityGraph(attributePaths = { "room", "student" })
    List<RoomAllocation> findByVacatedAtIsNull();
}
