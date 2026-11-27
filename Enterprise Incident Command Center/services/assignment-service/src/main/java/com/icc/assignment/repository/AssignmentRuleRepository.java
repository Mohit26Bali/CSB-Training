package com.icc.assignment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.icc.assignment.entity.AssignmentRule;

public interface AssignmentRuleRepository extends JpaRepository<AssignmentRule, Long> {

    Optional<AssignmentRule> findByCategoryAndActiveTrue(String category);
}
