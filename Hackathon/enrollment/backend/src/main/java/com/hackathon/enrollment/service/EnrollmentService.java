package com.hackathon.enrollment.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.enrollment.dto.EnrollmentResponse;
import com.hackathon.enrollment.dto.MessageResponse;
import com.hackathon.enrollment.entity.Course;
import com.hackathon.enrollment.entity.Enrollment;
import com.hackathon.enrollment.entity.EnrollmentStatus;
import com.hackathon.enrollment.entity.User;
import com.hackathon.enrollment.exception.CourseFullException;
import com.hackathon.enrollment.exception.CourseInactiveException;
import com.hackathon.enrollment.exception.DuplicateEnrollmentException;
import com.hackathon.enrollment.exception.InvalidStatusException;
import com.hackathon.enrollment.exception.ResourceNotFoundException;
import com.hackathon.enrollment.repo.CourseRepository;
import com.hackathon.enrollment.repo.EnrollmentRepository;
import com.hackathon.enrollment.repo.UserRepository;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             CourseRepository courseRepository,
                             UserRepository userRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    // READ_COMMITTED + course row lock: after waiting for the lock, the counts below see other requests'
    // commits (MySQL's default REPEATABLE READ would keep reading a snapshot taken before the wait).
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentResponse requestEnrollment(String username, Long courseId) {

        User user = findUser(username);

        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id " + courseId));

        if (!course.isActive()) {
            throw new CourseInactiveException("Course '" + course.getTitle() + "' is not active");
        }

        if (enrollmentRepository.existsByUserAndCourse(user, course)) {
            throw new DuplicateEnrollmentException("You have already requested this course");
        }

        requireFreeSeat(course);

        Enrollment enrollment = new Enrollment(user, course);
        try {
            return toResponse(enrollmentRepository.saveAndFlush(enrollment));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEnrollmentException("You have already requested this course");
        }
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getMyEnrollments(String username) {
        User user = findUser(username);
        return enrollmentRepository.findByUserOrderByRequestedAtDesc(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** An employee withdraws their own pending request. Someone else's request looks like "not found". */
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public MessageResponse cancel(String username, Long enrollmentId) {
        Enrollment enrollment = findEnrollmentForUpdate(enrollmentId);
        if (!enrollment.getUser().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Enrollment not found with id " + enrollmentId);
        }
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new InvalidStatusException(
                    "Only pending requests can be cancelled; this one is " + enrollment.getStatus());
        }
        enrollmentRepository.delete(enrollment);
        return new MessageResponse("Enrollment request cancelled");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getAllEnrollments(EnrollmentStatus status) {
        List<Enrollment> enrollments = (status == null)
                ? enrollmentRepository.findAllByOrderByRequestedAtDesc()
                : enrollmentRepository.findByStatusOrderByRequestedAtDesc(status);
        return enrollments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentResponse approve(Long enrollmentId, String adminUsername) {
        // Lock order is always enrollment, then course. The request path locks only the course.
        Enrollment enrollment = findEnrollmentForUpdate(enrollmentId);
        requirePending(enrollment);

        Course course = courseRepository.findByIdForUpdate(enrollment.getCourse().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!course.isActive()) {
            throw new CourseInactiveException("Course '" + course.getTitle() + "' is not active");
        }
        requireFreeSeat(course);

        enrollment.decide(EnrollmentStatus.APPROVED, adminUsername);
        return toResponse(enrollmentRepository.save(enrollment));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public EnrollmentResponse reject(Long enrollmentId, String adminUsername) {
        Enrollment enrollment = findEnrollmentForUpdate(enrollmentId);
        requirePending(enrollment);

        enrollment.decide(EnrollmentStatus.REJECTED, adminUsername);
        return toResponse(enrollmentRepository.save(enrollment));
    }

    /** Rejects every pending request of a course, e.g. after the course was deactivated. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MessageResponse rejectAllPending(Long courseId, String adminUsername) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id " + courseId));
        List<Enrollment> pending = enrollmentRepository.findByCourseAndStatus(course, EnrollmentStatus.PENDING);
        pending.forEach(e -> e.decide(EnrollmentStatus.REJECTED, adminUsername));
        enrollmentRepository.saveAll(pending);
        return new MessageResponse(pending.size() + " pending request(s) rejected");
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private Enrollment findEnrollmentForUpdate(Long id) {
        return enrollmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + id));
    }

    private void requireFreeSeat(Course course) {
        long approved = enrollmentRepository.countByCourseAndStatus(course, EnrollmentStatus.APPROVED);
        if (approved >= course.getCapacity()) {
            throw new CourseFullException("Course '" + course.getTitle() + "' is full");
        }
    }

    private void requirePending(Enrollment enrollment) {
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new InvalidStatusException(
                    "Enrollment is already " + enrollment.getStatus() + " and cannot be changed");
        }
    }

    private EnrollmentResponse toResponse(Enrollment e) {
        return new EnrollmentResponse(e.getId(), e.getCourse().getId(), e.getCourse().getTitle(),
                e.getUser().getUsername(), e.getUser().getFullname(),
                e.getStatus(), e.getRequestedAt(), e.getDecidedBy(), e.getDecidedAt());
    }
}
