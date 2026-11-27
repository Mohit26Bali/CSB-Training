package com.hackathon.scholarship.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.hackathon.scholarship.dto.ApplicationRequest;
import com.hackathon.scholarship.dto.ApplicationResponse;
import com.hackathon.scholarship.dto.ProgramRequest;
import com.hackathon.scholarship.dto.ProgramResponse;
import com.hackathon.scholarship.dto.Statistics;
import com.hackathon.scholarship.entity.ApplicationStatus;
import com.hackathon.scholarship.entity.Role;
import com.hackathon.scholarship.entity.ScholarshipProgram;
import com.hackathon.scholarship.entity.User;
import com.hackathon.scholarship.exception.BusinessRuleException;
import com.hackathon.scholarship.exception.ConflictException;
import com.hackathon.scholarship.exception.FieldValidationException;
import com.hackathon.scholarship.exception.InvalidStatusException;
import com.hackathon.scholarship.exception.ResourceNotFoundException;
import com.hackathon.scholarship.repo.ApplicationRepository;
import com.hackathon.scholarship.repo.ScholarshipProgramRepository;
import com.hackathon.scholarship.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class ScholarshipServiceTest {

    @Autowired ApplicationService applications;
    @Autowired ProgramService programService;
    @Autowired ScholarshipProgramRepository programs;
    @Autowired ApplicationRepository applicationRepo;
    @Autowired UserRepository users;

    ScholarshipProgram merit;   // min CGPA 8.0, income ceiling 1,000,000, open
    ScholarshipProgram closed;  // deadline passed
    ScholarshipProgram inactive;

    @BeforeEach
    void setUp() {
        applicationRepo.deleteAll();
        programs.deleteAll();
        users.deleteAll();
        users.save(new User("admin", "x", "Admin One", Role.SCHOLARSHIP_ADMIN));
        users.save(new User("admin2", "x", "Admin Two", Role.SCHOLARSHIP_ADMIN));
        IntStream.rangeClosed(1, 5).forEach(i -> users.save(new User("stu" + i, "x", "Student " + i, Role.STUDENT)));
        merit = programs.save(new ScholarshipProgram("Merit", "Top grades", new BigDecimal("50000"),
                LocalDate.now().plusDays(10), new BigDecimal("8.00"), new BigDecimal("1000000")));
        closed = programs.save(new ScholarshipProgram("Closed", null, new BigDecimal("1000"),
                LocalDate.now().minusDays(1), new BigDecimal("5.00"), new BigDecimal("1000000")));
        inactive = new ScholarshipProgram("Off", null, new BigDecimal("1000"), LocalDate.now().plusDays(10),
                new BigDecimal("5.00"), new BigDecimal("1000000"));
        inactive.update("Off", null, new BigDecimal("1000"), LocalDate.now().plusDays(10), new BigDecimal("5.00"),
                new BigDecimal("1000000"), false);
        inactive = programs.save(inactive);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    private ApplicationRequest request(ScholarshipProgram p, String gpa, String income) {
        return new ApplicationRequest(p.getId(), new BigDecimal(gpa), new BigDecimal(income), "B.Tech CS", 2, "I need support");
    }

    private ApplicationResponse apply(String student, ScholarshipProgram p, String gpa, String income) {
        loginAs(student, Role.STUDENT);
        return applications.apply(student, request(p, gpa, income));
    }

    // ----- eligibility -----

    @Test
    void scoreCombinesAcademicsAndNeed() {
        // CGPA 9 -> 54 points; income 250,000 of a 1,000,000 ceiling -> 30 points
        assertEquals(84, apply("stu1", merit, "9.00", "250000").eligibilityScore());
        // CGPA 8 -> 48 points; income at the ceiling -> 0 points
        assertEquals(48, apply("stu2", merit, "8.00", "1000000").eligibilityScore());
    }

    @Test
    void meetsCriteriaNeedsEnoughCgpaAndLowEnoughIncome() {
        assertTrue(apply("stu1", merit, "8.00", "1000000").meetsCriteria());
        assertFalse(apply("stu2", merit, "7.99", "100000").meetsCriteria());
        assertFalse(apply("stu3", merit, "9.50", "1000001").meetsCriteria());
    }

    // ----- applying -----

    @Test
    void aStudentCannotApplyTwiceToTheSameScholarship() {
        apply("stu1", merit, "9.00", "100000");
        assertThrows(ConflictException.class, () -> apply("stu1", merit, "9.10", "100000"));
        assertEquals(1, applicationRepo.count());
    }

    @Test
    void differentStudentsMayApplyToTheSameScholarship() {
        apply("stu1", merit, "9.00", "100000");
        assertEquals("SUBMITTED", apply("stu2", merit, "8.20", "300000").status());
    }

    @Test
    void closedInactiveAndUnknownScholarshipsRefuseApplications() {
        assertTrue(assertThrows(BusinessRuleException.class, () -> apply("stu1", closed, "9", "1")).getMessage().contains("closed"));
        assertTrue(assertThrows(BusinessRuleException.class, () -> apply("stu1", inactive, "9", "1")).getMessage().contains("not accepting"));
        loginAs("stu1", Role.STUDENT);
        assertEquals("programId", assertThrows(FieldValidationException.class, () -> applications.apply("stu1",
                new ApplicationRequest(999_999L, BigDecimal.ONE, BigDecimal.ONE, "X", 1, "Y"))).getField());
    }

    @Test
    void theDeadlineDayItselfIsStillOpen() {
        ScholarshipProgram today = programs.save(new ScholarshipProgram("Today", null, new BigDecimal("1000"),
                LocalDate.now(), new BigDecimal("5.00"), new BigDecimal("1000000")));
        assertEquals("SUBMITTED", apply("stu1", today, "7", "100000").status());
    }

    @Test
    void adminsCannotApplyAndStudentsCannotUseAdminFeatures() {
        loginAs("admin", Role.SCHOLARSHIP_ADMIN);
        assertThrows(AccessDeniedException.class, () -> applications.apply("admin", request(merit, "9", "1")));
        ApplicationResponse a = apply("stu1", merit, "9", "100000");
        loginAs("stu1", Role.STUDENT);
        assertThrows(AccessDeniedException.class, () -> applications.list(null, null, null));
        assertThrows(AccessDeniedException.class, () -> applications.approve(a.id(), "stu1", "Me"));
        assertThrows(AccessDeniedException.class, () -> applications.statistics());
        assertThrows(AccessDeniedException.class, () -> programService.listAll());
    }

    @Test
    void studentsSeeOnlyTheirOwnApplications() {
        apply("stu1", merit, "9", "100000");
        apply("stu2", merit, "9", "100000");
        loginAs("stu1", Role.STUDENT);
        List<ApplicationResponse> mine = applications.myApplications("stu1");
        assertEquals(1, mine.size());
        assertEquals("stu1", mine.get(0).studentUsername());
    }

    // ----- deciding -----

    @Test
    void approvingAndRejectingBothNeedAReason() {
        ApplicationResponse a = apply("stu1", merit, "9", "100000");
        loginAs("admin", Role.SCHOLARSHIP_ADMIN);
        assertEquals("reason", assertThrows(FieldValidationException.class, () -> applications.approve(a.id(), "admin", " ")).getField());
        assertEquals("reason", assertThrows(FieldValidationException.class, () -> applications.reject(a.id(), "admin", null)).getField());

        ApplicationResponse approved = applications.approve(a.id(), "admin", " Great record ");
        assertEquals("APPROVED", approved.status());
        assertEquals("Great record", approved.decisionReason());
        assertEquals("admin", approved.decidedBy());
        assertNotNull(approved.decidedAt());
    }

    @Test
    void aDecidedApplicationCannotBeDecidedAgain() {
        ApplicationResponse a = apply("stu1", merit, "9", "100000");
        loginAs("admin", Role.SCHOLARSHIP_ADMIN);
        applications.reject(a.id(), "admin", "Not this round");
        assertThrows(InvalidStatusException.class, () -> applications.approve(a.id(), "admin2", "Changed my mind"));
        assertThrows(InvalidStatusException.class, () -> applications.reject(a.id(), "admin2", "Again"));
        assertThrows(ResourceNotFoundException.class, () -> applications.approve(999_999L, "admin", "x"));
    }

    // ----- admin lists and statistics -----

    @Test
    void adminFiltersByProgramStatusAndText() {
        ApplicationResponse a = apply("stu1", merit, "9", "100000");
        apply("stu2", merit, "8", "100000");
        loginAs("admin", Role.SCHOLARSHIP_ADMIN);
        applications.approve(a.id(), "admin", "Good");
        assertEquals(2, applications.list(merit.getId(), null, null).size());
        assertEquals(1, applications.list(null, ApplicationStatus.APPROVED, null).size());
        assertEquals(1, applications.list(null, null, "student 2").size());
        assertEquals(0, applications.list(closed.getId(), null, null).size());
    }

    @Test
    void statisticsAddUp() {
        ApplicationResponse a = apply("stu1", merit, "9", "100000");
        ApplicationResponse b = apply("stu2", merit, "8", "100000");
        apply("stu3", merit, "8", "100000");
        loginAs("admin", Role.SCHOLARSHIP_ADMIN);
        applications.approve(a.id(), "admin", "Yes");
        applications.reject(b.id(), "admin", "No");

        Statistics s = applications.statistics();
        assertEquals(3, s.totalApplications());
        assertEquals(1, s.submitted());
        assertEquals(1, s.approved());
        assertEquals(1, s.rejected());
        assertEquals(0.5, s.approvalRate());
        assertEquals(0, new BigDecimal("50000").compareTo(s.totalAwarded()));
        assertEquals(3, s.byProgram().size());
    }

    // ----- programmes -----

    @Test
    void adminCreatesAndEditsProgrammesWithUniqueNames() {
        loginAs("admin", Role.SCHOLARSHIP_ADMIN);
        ProgramRequest req = new ProgramRequest(" Arts Prize ", " For artists ", new BigDecimal("20000"),
                LocalDate.now().plusDays(30), new BigDecimal("6.50"), new BigDecimal("700000"), null);
        ProgramResponse created = programService.create(req);
        assertEquals("Arts Prize", created.name());
        assertTrue(created.active());
        assertTrue(created.open());

        assertEquals("name", assertThrows(FieldValidationException.class, () -> programService.create(
                new ProgramRequest("arts prize", null, new BigDecimal("1"), LocalDate.now(), BigDecimal.ONE, BigDecimal.TEN, true))).getField());

        ProgramResponse closedNow = programService.update(created.id(), new ProgramRequest("Arts Prize", null,
                new BigDecimal("20000"), LocalDate.now().minusDays(2), new BigDecimal("6.50"), new BigDecimal("700000"), false));
        assertFalse(closedNow.active());
        assertFalse(closedNow.open());
        assertThrows(ResourceNotFoundException.class, () -> programService.update(999_999L, req));
    }

    @Test
    void studentsSeeActiveProgrammesOnlyAndOpenOnesAreFlagged() {
        loginAs("stu1", Role.STUDENT);
        List<ProgramResponse> list = programService.listActive();
        assertEquals(2, list.size()); // Merit + Closed; "Off" is inactive
        assertTrue(list.stream().anyMatch(p -> p.name().equals("Merit") && p.open()));
        assertTrue(list.stream().anyMatch(p -> p.name().equals("Closed") && !p.open()));
    }

    // ----- concurrency -----

    @Test
    void doubleClickingApplyCreatesOnlyOneApplication() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("stu1", Role.STUDENT);
                try {
                    applications.apply("stu1", request(merit, "9.00", "100000"));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(jobs)) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertEquals(1, applicationRepo.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoAdminsCannotDecideTheSameApplicationAtOnce() throws Exception {
        ApplicationResponse a = apply("stu1", merit, "9", "100000");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                String admin = i % 2 == 0 ? "admin" : "admin2";
                loginAs(admin, Role.SCHOLARSHIP_ADMIN);
                try {
                    if (i < 2) {
                        applications.approve(a.id(), admin, "Yes");
                    } else {
                        applications.reject(a.id(), admin, "No");
                    }
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(jobs)) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }
}
