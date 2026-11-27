package com.hackathon.parking.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.parking.entity.ParkingRequest;
import com.hackathon.parking.entity.RequestStatus;
import com.hackathon.parking.entity.User;

public interface ParkingRequestRepository extends JpaRepository<ParkingRequest, Long> {

    /** Plain values, so the request itself is not loaded before the locks are taken. */
    interface Peek {
        String getEmployeeUsername();
    }

    @Query("select r.employee.username as employeeUsername from ParkingRequest r where r.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    boolean existsByEmployeeAndStatus(User employee, RequestStatus status);

    @EntityGraph(attributePaths = { "employee", "slot" })
    List<ParkingRequest> findByEmployeeOrderByCreatedAtDesc(User employee);

    @EntityGraph(attributePaths = { "employee", "slot" })
    List<ParkingRequest> findAllByOrderByCreatedAtAsc();
}
