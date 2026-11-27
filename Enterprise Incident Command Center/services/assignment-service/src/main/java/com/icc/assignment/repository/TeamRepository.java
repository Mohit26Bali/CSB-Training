package com.icc.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.icc.assignment.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
