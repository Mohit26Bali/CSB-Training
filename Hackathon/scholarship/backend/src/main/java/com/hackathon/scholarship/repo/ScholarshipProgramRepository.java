package com.hackathon.scholarship.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.scholarship.entity.ScholarshipProgram;

public interface ScholarshipProgramRepository extends JpaRepository<ScholarshipProgram, Long> {

    List<ScholarshipProgram> findByActiveTrueOrderByDeadlineAsc();

    List<ScholarshipProgram> findAllByOrderByDeadlineAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
