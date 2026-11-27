package com.hackathon.enrollment.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.enrollment.dto.CourseStatusCount;
import com.hackathon.enrollment.entity.Course;
import com.hackathon.enrollment.entity.Enrollment;
import com.hackathon.enrollment.entity.EnrollmentStatus;
import com.hackathon.enrollment.entity.User;

import jakarta.persistence.LockModeType;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByUserAndCourse(User user, Course course);

    @EntityGraph(attributePaths = { "user", "course" })
    List<Enrollment> findByUserOrderByRequestedAtDesc(User user);

    @EntityGraph(attributePaths = { "user", "course" })
    List<Enrollment> findAllByOrderByRequestedAtDesc();

    @EntityGraph(attributePaths = { "user", "course" })
    List<Enrollment> findByStatusOrderByRequestedAtDesc(EnrollmentStatus status);

    @EntityGraph(attributePaths = { "user", "course" })
    List<Enrollment> findByCourseAndStatus(Course course, EnrollmentStatus status);

    long countByCourseAndStatus(Course course, EnrollmentStatus status);

    boolean existsByCourse(Course course);

    /** One query for every course's counts, instead of one count query per course. */
    @Query("select new com.hackathon.enrollment.dto.CourseStatusCount(e.course.id, e.status, count(e)) "
            + "from Enrollment e group by e.course.id, e.status")
    List<CourseStatusCount> countGroupedByCourseAndStatus();

    /** Row lock so two admins cannot decide the same request at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> findByIdForUpdate(@Param("id") Long id);
}
