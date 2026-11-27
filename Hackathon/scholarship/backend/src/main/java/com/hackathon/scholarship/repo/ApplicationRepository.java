package com.hackathon.scholarship.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.scholarship.entity.Application;
import com.hackathon.scholarship.entity.ScholarshipProgram;
import com.hackathon.scholarship.entity.User;

import jakarta.persistence.LockModeType;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByStudentAndProgram(User student, ScholarshipProgram program);

    @EntityGraph(attributePaths = { "student", "program" })
    List<Application> findByStudentOrderBySubmittedAtDesc(User student);

    @EntityGraph(attributePaths = { "student", "program" })
    List<Application> findAllByOrderBySubmittedAtDesc();

    /** Row lock so two admins cannot decide the same application at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") Long id);
}
