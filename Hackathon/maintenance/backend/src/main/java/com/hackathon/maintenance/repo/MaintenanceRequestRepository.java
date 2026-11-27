package com.hackathon.maintenance.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.maintenance.entity.MaintenanceRequest;
import com.hackathon.maintenance.entity.User;

import jakarta.persistence.LockModeType;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    @EntityGraph(attributePaths = { "resident", "technician" })
    List<MaintenanceRequest> findByResidentOrderByCreatedAtDesc(User resident);

    @EntityGraph(attributePaths = { "resident", "technician" })
    List<MaintenanceRequest> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "resident", "technician" })
    Optional<MaintenanceRequest> findWithPeopleById(Long id);

    /** Row lock so two managers cannot change the same request at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MaintenanceRequest r where r.id = :id")
    Optional<MaintenanceRequest> findByIdForUpdate(@Param("id") Long id);
}
