package com.icc.rca.repository;

import com.icc.rca.entity.ProblemIncident;
import com.icc.rca.entity.ProblemIncidentKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemIncidentRepository extends JpaRepository<ProblemIncident, ProblemIncidentKey> {

    // "key" is the embedded id, so key.problemId is written KeyProblemId.
    List<ProblemIncident> findByKeyProblemId(Long problemId);
}
