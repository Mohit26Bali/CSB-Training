package com.hackathon.enrollment.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.enrollment.entity.Course;

import jakarta.persistence.LockModeType;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByActiveTrue();

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    /** Takes a row lock (SELECT ... FOR UPDATE) so capacity checks on one course run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
