package com.hackathon.helpdesk.repo;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.hackathon.helpdesk.entity.Ticket;
import com.hackathon.helpdesk.entity.TicketComment;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    @EntityGraph(attributePaths = { "author" })
    List<TicketComment> findByTicketOrderByCreatedAtAsc(Ticket ticket);

    /** Ticket id and whether the comment is an internal note, for counting without loading the comments. */
    @Query("select c.ticket.id, c.internalNote from TicketComment c")
    List<Object[]> idsAndKinds();
}
