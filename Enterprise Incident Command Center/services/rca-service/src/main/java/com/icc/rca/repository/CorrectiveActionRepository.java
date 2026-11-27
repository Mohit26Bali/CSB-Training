package com.icc.rca.repository;

import com.icc.rca.entity.ActionStatus;
import com.icc.rca.entity.CorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {

    List<CorrectiveAction> findByProblemIdOrderByIdAsc(Long problemId);

    long countByProblemIdAndStatus(Long problemId, ActionStatus status);

    long countByStatus(ActionStatus status);
}
