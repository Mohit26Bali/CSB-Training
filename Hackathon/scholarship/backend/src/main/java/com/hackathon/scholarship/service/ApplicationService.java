package com.hackathon.scholarship.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.scholarship.dto.ApplicationRequest;
import com.hackathon.scholarship.dto.ApplicationResponse;
import com.hackathon.scholarship.dto.ProgramStats;
import com.hackathon.scholarship.dto.Statistics;
import com.hackathon.scholarship.entity.Application;
import com.hackathon.scholarship.entity.ApplicationStatus;
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

@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final ScholarshipProgramRepository programs;
    private final UserRepository users;

    public ApplicationService(ApplicationRepository applications, ScholarshipProgramRepository programs,
                              UserRepository users) {
        this.applications = applications;
        this.programs = programs;
        this.users = users;
    }

    // ---------- student ----------

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public ApplicationResponse apply(String username, ApplicationRequest request) {
        User student = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        ScholarshipProgram program = programs.findById(request.programId())
                .orElseThrow(() -> new FieldValidationException("programId", "That scholarship does not exist"));

        LocalDate today = LocalDate.now();
        if (!program.isActive()) {
            throw new BusinessRuleException("'" + program.getName() + "' is not accepting applications");
        }
        if (program.getDeadline().isBefore(today)) {
            throw new BusinessRuleException("Applications for '" + program.getName() + "' closed on " + program.getDeadline());
        }
        if (applications.existsByStudentAndProgram(student, program)) {
            throw new ConflictException("You have already applied for '" + program.getName() + "'");
        }

        Application application = new Application(student, program, request.gpa(), request.annualFamilyIncome(),
                request.course().trim(), request.yearOfStudy(), request.statement().trim(),
                EligibilityCalculator.score(request.gpa(), request.annualFamilyIncome(), program),
                EligibilityCalculator.meetsCriteria(request.gpa(), request.annualFamilyIncome(), program));
        try {
            return ApplicationResponse.from(applications.saveAndFlush(application));
        } catch (DataIntegrityViolationException ex) { // two clicks at once: the unique constraint decided
            throw new ConflictException("You have already applied for '" + program.getName() + "'");
        }
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public List<ApplicationResponse> myApplications(String username) {
        User student = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return applications.findByStudentOrderBySubmittedAtDesc(student).stream()
                .map(ApplicationResponse::from).collect(Collectors.toList());
    }

    // ---------- admin ----------

    @PreAuthorize("hasRole('SCHOLARSHIP_ADMIN')")
    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(Long programId, ApplicationStatus status, String search) {
        String term = (search == null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        return applications.findAllByOrderBySubmittedAtDesc().stream()
                .filter(a -> programId == null || a.getProgram().getId().equals(programId))
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> term.isEmpty()
                        || a.getStudent().getFullname().toLowerCase(Locale.ROOT).contains(term)
                        || a.getStudent().getUsername().toLowerCase(Locale.ROOT).contains(term)
                        || a.getCourse().toLowerCase(Locale.ROOT).contains(term))
                .map(ApplicationResponse::from)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SCHOLARSHIP_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationResponse approve(Long id, String admin, String reason) {
        return decide(id, ApplicationStatus.APPROVED, admin, reason);
    }

    @PreAuthorize("hasRole('SCHOLARSHIP_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationResponse reject(Long id, String admin, String reason) {
        return decide(id, ApplicationStatus.REJECTED, admin, reason);
    }

    @PreAuthorize("hasRole('SCHOLARSHIP_ADMIN')")
    @Transactional(readOnly = true)
    public Statistics statistics() {
        List<Application> all = applications.findAllByOrderBySubmittedAtDesc();
        Map<ScholarshipProgram, List<Application>> byProgram = all.stream()
                .collect(Collectors.groupingBy(Application::getProgram));

        List<ProgramStats> perProgram = programs.findAllByOrderByDeadlineAsc().stream().map(p -> {
            List<Application> list = byProgram.getOrDefault(p, List.of());
            long approved = count(list, ApplicationStatus.APPROVED);
            double average = list.stream().mapToInt(Application::getEligibilityScore).average().orElse(0);
            return new ProgramStats(p.getId(), p.getName(), list.size(), count(list, ApplicationStatus.SUBMITTED),
                    approved, count(list, ApplicationStatus.REJECTED), Math.round(average * 10) / 10.0,
                    p.getAmount().multiply(BigDecimal.valueOf(approved)));
        }).collect(Collectors.toList());

        long approved = count(all, ApplicationStatus.APPROVED);
        long rejected = count(all, ApplicationStatus.REJECTED);
        double rate = (approved + rejected) == 0 ? 0 : (double) approved / (approved + rejected);
        BigDecimal awarded = perProgram.stream().map(ProgramStats::awardedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Statistics(all.size(), count(all, ApplicationStatus.SUBMITTED), approved, rejected,
                Math.round(rate * 1000) / 1000.0, awarded, perProgram);
    }

    // ---------- helpers ----------

    private ApplicationResponse decide(Long id, ApplicationStatus decision, String admin, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new FieldValidationException("reason", "A reason is required for the decision");
        }
        Application application = applications.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id " + id));
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new InvalidStatusException("This application is already " + application.getStatus().name().toLowerCase(Locale.ROOT)
                    + " and cannot be decided again");
        }
        application.decide(decision, admin, reason.trim());
        return ApplicationResponse.from(application);
    }

    private long count(List<Application> list, ApplicationStatus status) {
        return list.stream().filter(a -> a.getStatus() == status).count();
    }
}
