package com.hackathon.complaints.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.complaints.entity.Complaint;
import com.hackathon.complaints.entity.User;

import jakarta.persistence.LockModeType;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    @EntityGraph(attributePaths = { "customer", "assignee" })
    List<Complaint> findByCustomerOrderByCreatedAtDesc(User customer);

    @EntityGraph(attributePaths = { "customer", "assignee" })
    List<Complaint> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "customer", "assignee" })
    Optional<Complaint> findWithPeopleById(Long id);

    /** Row lock so two agents (or an agent and the customer's feedback) cannot change one complaint at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.id = :id")
    Optional<Complaint> findByIdForUpdate(@Param("id") Long id);
}
