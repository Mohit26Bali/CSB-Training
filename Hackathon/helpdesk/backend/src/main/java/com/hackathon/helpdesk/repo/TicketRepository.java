package com.hackathon.helpdesk.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.helpdesk.entity.Ticket;
import com.hackathon.helpdesk.entity.User;

import jakarta.persistence.LockModeType;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @EntityGraph(attributePaths = { "requester", "assignee" })
    List<Ticket> findByRequesterOrderByCreatedAtDesc(User requester);

    @EntityGraph(attributePaths = { "requester", "assignee" })
    List<Ticket> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "requester", "assignee" })
    Optional<Ticket> findWithPeopleById(Long id);

    /** Row lock so two agents cannot change the same ticket at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);
}
