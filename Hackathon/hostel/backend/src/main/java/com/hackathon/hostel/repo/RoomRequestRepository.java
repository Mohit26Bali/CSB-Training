package com.hackathon.hostel.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.hostel.entity.RequestStatus;
import com.hackathon.hostel.entity.RoomRequest;
import com.hackathon.hostel.entity.User;

public interface RoomRequestRepository extends JpaRepository<RoomRequest, Long> {

    /** Plain values, so the request itself is not loaded before the locks are taken. */
    interface Peek {
        String getStudentUsername();
    }

    @Query("select r.student.username as studentUsername from RoomRequest r where r.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    boolean existsByStudentAndStatus(User student, RequestStatus status);

    @EntityGraph(attributePaths = { "student", "room" })
    List<RoomRequest> findByStudentOrderByCreatedAtDesc(User student);

    @EntityGraph(attributePaths = { "student", "room" })
    List<RoomRequest> findAllByOrderByCreatedAtAsc();
}
