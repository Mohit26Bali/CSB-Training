package com.hackathon.referral.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.hackathon.referral.dto.JobRequest;
import com.hackathon.referral.dto.JobResponse;
import com.hackathon.referral.dto.RecruiterDashboard;
import com.hackathon.referral.dto.ReferralRequest;
import com.hackathon.referral.dto.ReferralResponse;
import com.hackathon.referral.entity.Job;
import com.hackathon.referral.entity.JobStatus;
import com.hackathon.referral.entity.ReferralStatus;
import com.hackathon.referral.entity.Role;
import com.hackathon.referral.entity.User;
import com.hackathon.referral.exception.BusinessRuleException;
import com.hackathon.referral.exception.ConflictException;
import com.hackathon.referral.exception.FieldValidationException;
import com.hackathon.referral.exception.InvalidStatusException;
import com.hackathon.referral.exception.ResourceNotFoundException;
import com.hackathon.referral.repo.JobRepository;
import com.hackathon.referral.repo.ReferralRepository;
import com.hackathon.referral.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class ReferralServiceTest {

    @Autowired ReferralService referralService;
    @Autowired JobService jobService;
    @Autowired JobRepository jobs;
    @Autowired ReferralRepository referrals;
    @Autowired UserRepository users;

    Job open;
    Job draft;
    Job closed;

    @BeforeEach
    void setUp() {
        referrals.deleteAll();
        jobs.deleteAll();
        users.deleteAll();
        users.save(new User("rec", "x", "Recruiter One", Role.RECRUITER));
        users.save(new User("rec2", "x", "Recruiter Two", Role.RECRUITER));
        users.save(new User("emp1", "x", "Employee One", Role.EMPLOYEE));
        users.save(new User("emp2", "x", "Employee Two", Role.EMPLOYEE));
        open = publishedJob("Java Developer", List.of("Java", "Spring Boot"));
        draft = jobs.save(new Job("Architect", "Engineering", "Pune", "Not open yet", List.of("AWS"), "rec"));
        closed = publishedJob("Old role", List.of("COBOL"));
        closed.close();
        closed = jobs.save(closed);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    private Job publishedJob(String title, List<String> skills) {
        Job j = new Job(title, "Engineering", "Bengaluru", "Great role", skills, "rec");
        j.publish();
        return jobs.save(j);
    }

    private ReferralRequest request(Job job, String name, String email) {
        return new ReferralRequest(job.getId(), name, email, null, null, "Strong candidate");
    }

    private ReferralResponse refer(String employee, Job job, String name, String email) {
        loginAs(employee, Role.EMPLOYEE);
        return referralService.refer(employee, request(job, name, email));
    }

    // ----- referring -----

    @Test
    void employeeReferralsStartAsSubmitted() {
        ReferralResponse r = refer("emp1", open, "  Asha Rao ", "ASHA@Example.com");
        assertEquals("SUBMITTED", r.status());
        assertEquals("Asha Rao", r.candidateName());
        assertEquals("asha@example.com", r.candidateEmail());
        assertEquals("emp1", r.referrerUsername());
        assertEquals(List.of("SCREENING", "REJECTED"), r.allowedNext(), "next stage first, rejection last");
    }

    @Test
    void onlyPublishedJobsAcceptReferrals() {
        assertTrue(assertThrows(BusinessRuleException.class, () -> refer("emp1", draft, "A", "a@x.com")).getMessage().contains("not open"));
        assertThrows(BusinessRuleException.class, () -> refer("emp1", closed, "A", "a@x.com"));
        assertEquals("jobId", assertThrows(FieldValidationException.class, () -> {
            loginAs("emp1", Role.EMPLOYEE);
            referralService.refer("emp1", new ReferralRequest(999_999L, "A", "a@x.com", null, null, null));
        }).getField());
    }

    @Test
    void theSameCandidateCannotBeReferredTwiceForTheSameJobEvenByAnotherEmployee() {
        refer("emp1", open, "Asha", "asha@example.com");
        assertThrows(ConflictException.class, () -> refer("emp1", open, "Asha", "asha@example.com"));
        assertThrows(ConflictException.class, () -> refer("emp2", open, "Asha R", "ASHA@EXAMPLE.COM"));
        assertEquals(1, referrals.count());
    }

    @Test
    void theSameCandidateMayBeReferredForDifferentJobs() {
        Job other = publishedJob("QA Engineer", List.of("Selenium"));
        refer("emp1", open, "Asha", "asha@example.com");
        assertEquals("SUBMITTED", refer("emp1", other, "Asha", "asha@example.com").status());
    }

    @Test
    void recruitersCannotReferAndEmployeesCannotUseRecruiterFeatures() {
        loginAs("rec", Role.RECRUITER);
        assertThrows(AccessDeniedException.class, () -> referralService.refer("rec", request(open, "A", "a@x.com")));
        ReferralResponse r = refer("emp1", open, "Asha", "asha@example.com");
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> referralService.updateStatus(r.id(), "emp1", "SCREENING", null));
        assertThrows(AccessDeniedException.class, () -> referralService.list(null, null, null));
        assertThrows(AccessDeniedException.class, () -> referralService.dashboard());
        assertThrows(AccessDeniedException.class, () -> jobService.listAll(null));
        assertThrows(AccessDeniedException.class, () -> jobService.publish(draft.getId()));
    }

    @Test
    void employeesSeeOnlyTheirOwnReferrals() {
        refer("emp1", open, "Asha", "asha@example.com");
        refer("emp2", open, "Bala", "bala@example.com");
        loginAs("emp1", Role.EMPLOYEE);
        List<ReferralResponse> mine = referralService.myReferrals("emp1", null);
        assertEquals(1, mine.size());
        assertEquals("Asha", mine.get(0).candidateName());
        assertEquals(0, referralService.myReferrals("emp1", ReferralStatus.SELECTED).size());
    }

    // ----- pipeline -----

    @Test
    void aReferralMovesThroughTheWholePipelineOneStepAtATime() {
        ReferralResponse r = refer("emp1", open, "Asha", "asha@example.com");
        loginAs("rec", Role.RECRUITER);
        ReferralResponse screening = referralService.updateStatus(r.id(), "rec", "screening", " Phone screen booked ");
        assertEquals("SCREENING", screening.status());
        assertEquals("rec", screening.updatedBy());
        assertEquals("Phone screen booked", screening.statusNote());
        assertNotNull(screening.updatedAt());
        assertEquals("INTERVIEW", referralService.updateStatus(r.id(), "rec", "INTERVIEW", null).status());
        assertEquals("SELECTED", referralService.updateStatus(r.id(), "rec", "SELECTED", "Offer sent").status());
    }

    @Test
    void stepsCannotBeSkippedOrRepeatedOrGoBackwards() {
        ReferralResponse r = refer("emp1", open, "Asha", "asha@example.com");
        loginAs("rec", Role.RECRUITER);
        assertTrue(assertThrows(InvalidStatusException.class, () -> referralService.updateStatus(r.id(), "rec", "SELECTED", null))
                .getMessage().contains("screening or rejected"));
        assertThrows(InvalidStatusException.class, () -> referralService.updateStatus(r.id(), "rec", "INTERVIEW", null)); // skips screening
        assertThrows(InvalidStatusException.class, () -> referralService.updateStatus(r.id(), "rec", "SUBMITTED", null)); // same status
        referralService.updateStatus(r.id(), "rec", "SCREENING", null);
        assertThrows(InvalidStatusException.class, () -> referralService.updateStatus(r.id(), "rec", "SUBMITTED", null)); // backwards
        assertThrows(InvalidStatusException.class, () -> referralService.updateStatus(r.id(), "rec", "SCREENING", null)); // repeat
    }

    @Test
    void aReferralCanBeRejectedFromAnyOpenStageAndThenIsFinal() {
        ReferralResponse a = refer("emp1", open, "Asha", "asha@example.com");
        ReferralResponse b = refer("emp1", open, "Bala", "bala@example.com");
        ReferralResponse c = refer("emp1", open, "Chitra", "chitra@example.com");
        loginAs("rec", Role.RECRUITER);

        assertEquals("REJECTED", referralService.updateStatus(a.id(), "rec", "REJECTED", "Not a fit").status());
        referralService.updateStatus(b.id(), "rec", "SCREENING", null);
        assertEquals("REJECTED", referralService.updateStatus(b.id(), "rec", "REJECTED", null).status());
        referralService.updateStatus(c.id(), "rec", "SCREENING", null);
        referralService.updateStatus(c.id(), "rec", "INTERVIEW", null);
        assertEquals("REJECTED", referralService.updateStatus(c.id(), "rec", "REJECTED", null).status());

        assertThrows(InvalidStatusException.class, () -> referralService.updateStatus(a.id(), "rec", "SCREENING", null));
        assertEquals(List.of(), referralService.list(null, ReferralStatus.REJECTED, "asha").get(0).allowedNext());
    }

    @Test
    void selectedIsFinalAndUnknownStatusesAndIdsAreRefused() {
        ReferralResponse r = refer("emp1", open, "Asha", "asha@example.com");
        loginAs("rec", Role.RECRUITER);
        referralService.updateStatus(r.id(), "rec", "SCREENING", null);
        referralService.updateStatus(r.id(), "rec", "INTERVIEW", null);
        referralService.updateStatus(r.id(), "rec", "SELECTED", null);
        assertThrows(InvalidStatusException.class, () -> referralService.updateStatus(r.id(), "rec", "REJECTED", null));
        assertEquals("status", assertThrows(FieldValidationException.class, () -> referralService.updateStatus(r.id(), "rec", "HIRED", null)).getField());
        assertThrows(ResourceNotFoundException.class, () -> referralService.updateStatus(999_999L, "rec", "SCREENING", null));
    }

    // ----- jobs -----

    @Test
    void recruitersCreateEditPublishAndCloseJobs() {
        loginAs("rec", Role.RECRUITER);
        JobResponse created = jobService.create(new JobRequest(" Data Engineer ", "Analytics", "Remote", "Build pipelines",
                List.of("SQL", " sql ", "Spark")), "rec");
        assertEquals("DRAFT", created.status());
        assertEquals("Data Engineer", created.title());
        assertEquals(List.of("SQL", "Spark"), created.skills(), "duplicate skills (ignoring case) are removed");
        assertEquals(0, jobService.listOpen(null, null).stream().filter(j -> j.id().equals(created.id())).count(), "drafts are hidden");

        JobResponse edited = jobService.update(created.id(), new JobRequest("Data Engineer II", "Analytics", "Remote", "Build pipelines", List.of("SQL")));
        assertEquals("Data Engineer II", edited.title());

        assertEquals("PUBLISHED", jobService.publish(created.id()).status());
        assertThrows(InvalidStatusException.class, () -> jobService.publish(created.id()));
        assertEquals(1, jobService.listOpen("engineer", null).stream().filter(j -> j.id().equals(created.id())).count());

        assertEquals("CLOSED", jobService.close(created.id()).status());
        assertThrows(InvalidStatusException.class, () -> jobService.close(created.id()));
        assertThrows(InvalidStatusException.class, () -> jobService.close(draft.getId()));
        assertEquals("PUBLISHED", jobService.publish(created.id()).status(), "a closed job can be reopened");
        assertThrows(ResourceNotFoundException.class, () -> jobService.publish(999_999L));
    }

    @Test
    void closingAJobStopsNewReferralsButKeepsExistingOnes() {
        ReferralResponse r = refer("emp1", open, "Asha", "asha@example.com");
        loginAs("rec", Role.RECRUITER);
        jobService.close(open.getId());
        assertThrows(BusinessRuleException.class, () -> refer("emp2", open, "Bala", "bala@example.com"));
        loginAs("rec", Role.RECRUITER);
        assertEquals("SCREENING", referralService.updateStatus(r.id(), "rec", "SCREENING", null).status());
    }

    @Test
    void employeesSearchOpenJobsByTextAndBySkill() {
        publishedJob("React Engineer", List.of("React", "TypeScript"));
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals(1, jobService.listOpen("java", null).size()); // draft and closed jobs are never listed
        assertEquals(1, jobService.listOpen(null, "react").size());
        assertEquals(1, jobService.listOpen("typescript", null).size());
        assertEquals(2, jobService.listOpen(null, null).size());
        assertEquals(List.of("Java", "React", "Spring Boot", "TypeScript"), jobService.openSkills());
    }

    @Test
    void jobsRejectEmptyAndCommaSeparatedSkillsAndRecruitersSeeCounts() {
        loginAs("rec", Role.RECRUITER);
        assertEquals("skills", assertThrows(FieldValidationException.class, () -> jobService.create(
                new JobRequest("T", "D", "L", "X", List.of("  ")), "rec")).getField());
        assertEquals("skills", assertThrows(FieldValidationException.class, () -> jobService.create(
                new JobRequest("T", "D", "L", "X", List.of("Java, Spring")), "rec")).getField());
        refer("emp1", open, "Asha", "asha@example.com");
        loginAs("rec", Role.RECRUITER);
        assertEquals(1, jobService.listAll(JobStatus.PUBLISHED).stream().filter(j -> j.id().equals(open.getId())).findFirst().orElseThrow().referralCount());
        assertEquals(3, jobService.listAll(null).size());
    }

    // ----- lists and dashboard -----

    @Test
    void recruiterFiltersAndDashboardAddUp() {
        Job other = publishedJob("QA Engineer", List.of("Selenium"));
        ReferralResponse a = refer("emp1", open, "Asha", "asha@example.com");
        ReferralResponse b = refer("emp1", open, "Bala", "bala@example.com");
        refer("emp2", other, "Chitra", "chitra@example.com");
        loginAs("rec", Role.RECRUITER);
        referralService.updateStatus(a.id(), "rec", "SCREENING", null);
        referralService.updateStatus(b.id(), "rec", "REJECTED", null);

        assertEquals(3, referralService.list(null, null, null).size());
        assertEquals(2, referralService.list(open.getId(), null, null).size());
        assertEquals(1, referralService.list(null, ReferralStatus.SCREENING, null).size());
        assertEquals(1, referralService.list(null, null, "employee two").size());
        assertEquals(1, referralService.list(null, null, "BALA@example").size());

        RecruiterDashboard d = referralService.dashboard();
        assertEquals(3, d.totalReferrals());
        assertEquals(0, d.hires());
        assertEquals(2, d.openJobs(), "two jobs are published; the draft and the closed one are not open");
        assertEquals(5, d.byStatus().size());
        assertEquals(1, d.byStatus().stream().filter(s -> s.status().equals("REJECTED")).findFirst().orElseThrow().count());
        assertEquals(2, d.byJob().stream().filter(j -> j.jobId().equals(open.getId())).findFirst().orElseThrow().total());
    }

    // ----- concurrency -----

    @Test
    void referringTheSameCandidateAtTheSameMomentCreatesOneReferral() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> jobsToRun = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                String employee = i % 2 == 0 ? "emp1" : "emp2";
                loginAs(employee, Role.EMPLOYEE);
                try {
                    referralService.refer(employee, request(open, "Asha", "asha@example.com"));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(jobsToRun)) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertEquals(1, referrals.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoRecruitersCannotMoveTheSameReferralAtOnce() throws Exception {
        ReferralResponse r = refer("emp1", open, "Asha", "asha@example.com");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobsToRun = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                String recruiter = i % 2 == 0 ? "rec" : "rec2";
                loginAs(recruiter, Role.RECRUITER);
                try {
                    referralService.updateStatus(r.id(), recruiter, "SCREENING", null);
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(jobsToRun)) {
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
