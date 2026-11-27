package com.icc.rca.repository;

import com.icc.rca.entity.Problem;
import com.icc.rca.entity.ProblemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Page<Problem> findByStatus(ProblemStatus status, Pageable pageable);

    @Query("select p.status, count(p) from Problem p group by p.status")
    List<Object[]> countGroupedByStatus();
}
