package com.hackathon.complaints.repo;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.complaints.entity.Complaint;
import com.hackathon.complaints.entity.ComplaintEvent;

public interface ComplaintEventRepository extends JpaRepository<ComplaintEvent, Long> {

    @EntityGraph(attributePaths = { "actor" })
    List<ComplaintEvent> findByComplaintOrderByCreatedAtAscIdAsc(Complaint complaint);
}
