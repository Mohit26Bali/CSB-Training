package com.hackathon.asset.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.asset.entity.Asset;
import com.hackathon.asset.entity.Assignment;
import com.hackathon.asset.entity.AssignmentStatus;
import com.hackathon.asset.entity.User;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /** Plain values, so the assignment is not loaded before the asset row is locked. */
    interface Peek {
        Long getAssetId();

        String getEmployeeUsername();
    }

    @Query("select a.asset.id as assetId, a.employee.username as employeeUsername from Assignment a where a.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    @EntityGraph(attributePaths = { "asset", "employee" })
    Optional<Assignment> findDetailedById(Long id);

    @EntityGraph(attributePaths = { "asset", "employee" })
    List<Assignment> findByStatusInOrderByAssignedAtAsc(Collection<AssignmentStatus> statuses);

    @EntityGraph(attributePaths = { "asset", "employee" })
    List<Assignment> findByAssetOrderByAssignedAtDesc(Asset asset);

    @EntityGraph(attributePaths = { "asset", "employee" })
    List<Assignment> findByEmployeeOrderByAssignedAtDesc(User employee);

    long countByEmployeeAndStatusIn(User employee, Collection<AssignmentStatus> statuses);

    @EntityGraph(attributePaths = { "asset", "employee" })
    List<Assignment> findAllByOrderByAssignedAtDesc();
}
