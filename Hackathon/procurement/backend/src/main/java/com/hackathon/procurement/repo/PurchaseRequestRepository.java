package com.hackathon.procurement.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.procurement.entity.PurchaseRequest;
import com.hackathon.procurement.entity.User;

import jakarta.persistence.LockModeType;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    @EntityGraph(attributePaths = "employee")
    List<PurchaseRequest> findByEmployeeOrderBySubmittedAtDesc(User employee);

    @EntityGraph(attributePaths = "employee")
    List<PurchaseRequest> findAllByOrderBySubmittedAtDesc();

    /** Requests whose approval falls in [from, to) and that are still committed (approved, ordered, delivered). */
    @Query("select r from PurchaseRequest r where r.decidedAt >= :from and r.decidedAt < :to "
            + "and r.status in (com.hackathon.procurement.entity.RequestStatus.APPROVED, "
            + "com.hackathon.procurement.entity.RequestStatus.ORDERED, com.hackathon.procurement.entity.RequestStatus.DELIVERED)")
    List<PurchaseRequest> findCommittedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Row lock so two managers cannot move the same request at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PurchaseRequest r where r.id = :id")
    Optional<PurchaseRequest> findByIdForUpdate(@Param("id") Long id);
}
