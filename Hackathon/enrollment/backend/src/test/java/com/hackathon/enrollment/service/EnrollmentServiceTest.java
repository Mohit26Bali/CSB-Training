package com.hackathon.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.hackathon.enrollment.dto.CourseRequest;
import com.hackathon.enrollment.dto.CourseResponse;
import com.hackathon.enrollment.dto.EnrollmentResponse;
import com.hackathon.enrollment.entity.Course;
import com.hackathon.enrollment.entity.EnrollmentStatus;
import com.hackathon.enrollment.entity.Role;
import com.hackathon.enrollment.entity.User;
import com.hackathon.enrollment.exception.CourseFullException;
import com.hackathon.enrollment.exception.CourseInactiveException;
import com.hackathon.enrollment.exception.DuplicateCourseException;
import com.hackathon.enrollment.exception.DuplicateEnrollmentException;
import com.hackathon.enrollment.exception.InvalidCapacityException;
import com.hackathon.enrollment.exception.InvalidStatusException;
import com.hackathon.enrollment.exception.ResourceNotFoundException;
import com.hackathon.enrollment.repo.CourseRepository;
import com.hackathon.enrollment.repo.EnrollmentRepository;
import com.hackathon.enrollment.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentServiceTest {

    @Autowired EnrollmentService enrollmentService;
    @Autowired CourseService courseService;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("admin", "x", "Admin", Role.ADMIN));
        IntStream.rangeClosed(1, 5)
                .forEach(i -> userRepository.save(new User("emp" + i, "x", "Employee " + i, Role.EMPLOYEE)));
        loginAs("admin", "ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    private Course course(String title, int capacity, boolean active) {
        Course c = new Course(title, "d", "Cat", capacity);
        c.setActive(active);
        return courseRepository.save(c);
    }

    private EnrollmentResponse request(String username, Course course) {
        loginAs(username, "EMPLOYEE");
        try {
            return enrollmentService.requestEnrollment(username, course.getId());
        } finally {
            loginAs("admin", "ADMIN");
        }
    }

    @Test
    void sameCourseTwiceIsRejected() {
        Course c = course("Java", 5, true);
        request("emp1", c);
        assertThrows(DuplicateEnrollmentException.class, () -> request("emp1", c));
    }

    @Test
    void inactiveCourseRejectsRequests() {
        Course c = course("Old", 5, false);
        assertThrows(CourseInactiveException.class, () -> request("emp1", c));
    }

    @Test
    void pendingRequestsDoNotUseSeatsButApprovalsDo() {
        Course c = course("Java", 1, true);
        EnrollmentResponse first = request("emp1", c);
        EnrollmentResponse second = request("emp2", c);
        enrollmentService.approve(first.getId(), "admin");

        assertThrows(CourseFullException.class, () -> enrollmentService.approve(second.getId(), "admin"));
        assertThrows(CourseFullException.class, () -> request("emp3", c));
    }

    @Test
    void decidedRequestsCannotBeDecidedAgainAndRecordWhoDecided() {
        Course c = course("Java", 5, true);
        EnrollmentResponse e = request("emp1", c);
        EnrollmentResponse approved = enrollmentService.approve(e.getId(), "admin");

        assertEquals("admin", approved.getDecidedBy());
        assertThrows(InvalidStatusException.class, () -> enrollmentService.approve(e.getId(), "admin"));
        assertThrows(InvalidStatusException.class, () -> enrollmentService.reject(e.getId(), "admin"));
    }

    @Test
    void approvingIntoADeactivatedCourseIsRejected() {
        Course c = course("Java", 5, true);
        EnrollmentResponse e = request("emp1", c);
        c.setActive(false);
        courseRepository.save(c);
        assertThrows(CourseInactiveException.class, () -> enrollmentService.approve(e.getId(), "admin"));
    }

    @Test
    void capacityCannotDropBelowApprovedSeats() {
        Course c = course("Java", 3, true);
        enrollmentService.approve(request("emp1", c).getId(), "admin");
        enrollmentService.approve(request("emp2", c).getId(), "admin");

        assertThrows(InvalidCapacityException.class, () -> courseService.updateCourse(c.getId(), courseRequest("Java", 1)));
        CourseResponse updated = courseService.updateCourse(c.getId(), courseRequest("Java", 2));
        assertEquals(2, updated.getCapacity());
        assertEquals(2, updated.getApprovedCount());
    }

    @Test
    void courseTitlesMustBeUniqueIgnoringCase() {
        courseService.createCourse(courseRequest("Docker", 5));
        assertThrows(DuplicateCourseException.class, () -> courseService.createCourse(courseRequest("  docker ", 5)));
    }

    @Test
    void employeeCancelsOnlyOwnPendingRequests() {
        Course c = course("Java", 5, true);
        EnrollmentResponse mine = request("emp1", c);
        EnrollmentResponse other = request("emp2", c);

        loginAs("emp1", "EMPLOYEE");
        assertThrows(ResourceNotFoundException.class, () -> enrollmentService.cancel("emp1", other.getId()));
        enrollmentService.cancel("emp1", mine.getId());
        assertEquals(0, enrollmentService.getMyEnrollments("emp1").size());

        loginAs("admin", "ADMIN");
        EnrollmentResponse decided = request("emp3", c);
        enrollmentService.reject(decided.getId(), "admin");
        loginAs("emp3", "EMPLOYEE");
        assertThrows(InvalidStatusException.class, () -> enrollmentService.cancel("emp3", decided.getId()));
    }

    @Test
    void rejectAllPendingClearsTheQueue() {
        Course c = course("Java", 5, true);
        request("emp1", c);
        request("emp2", c);
        enrollmentService.rejectAllPending(c.getId(), "admin");
        assertEquals(2, enrollmentService.getAllEnrollments(EnrollmentStatus.REJECTED).size());
    }

    @Test
    void concurrentApprovalsNeverExceedCapacity() throws Exception {
        Course c = course("Last seat", 1, true);
        List<EnrollmentResponse> pending = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> request("emp" + i, c)).toList();

        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = pending.stream().<Callable<Boolean>>map(p -> () -> {
                loginAs("admin", "ADMIN");
                try {
                    enrollmentService.approve(p.getId(), "admin");
                    return true;
                } catch (CourseFullException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long succeeded = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) {
                    succeeded++;
                }
            }
            assertEquals(1, succeeded);
            assertEquals(1, enrollmentRepository.countByCourseAndStatus(c, EnrollmentStatus.APPROVED));
        } finally {
            pool.shutdownNow();
        }
    }

    private static CourseRequest courseRequest(String title, int capacity) {
        CourseRequest r = new CourseRequest();
        r.setTitle(title);
        r.setCategory("Cat");
        r.setCapacity(capacity);
        return r;
    }
}
