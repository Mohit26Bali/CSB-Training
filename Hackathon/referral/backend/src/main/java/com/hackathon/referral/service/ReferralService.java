package com.hackathon.referral.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.referral.dto.JobStats;
import com.hackathon.referral.dto.RecruiterDashboard;
import com.hackathon.referral.dto.ReferralRequest;
import com.hackathon.referral.dto.ReferralResponse;
import com.hackathon.referral.dto.StatusCount;
import com.hackathon.referral.entity.Job;
import com.hackathon.referral.entity.JobStatus;
import com.hackathon.referral.entity.Referral;
import com.hackathon.referral.entity.ReferralStatus;
import com.hackathon.referral.entity.User;
import com.hackathon.referral.exception.BusinessRuleException;
import com.hackathon.referral.exception.ConflictException;
import com.hackathon.referral.exception.FieldValidationException;
import com.hackathon.referral.exception.InvalidStatusException;
import com.hackathon.referral.exception.ResourceNotFoundException;
import com.hackathon.referral.repo.JobRepository;
import com.hackathon.referral.repo.ReferralRepository;
import com.hackathon.referral.repo.UserRepository;

@Service
public class ReferralService {

    private final ReferralRepository referrals;
    private final JobRepository jobs;
    private final UserRepository users;

    public ReferralService(ReferralRepository referrals, JobRepository jobs, UserRepository users) {
        this.referrals = referrals;
        this.jobs = jobs;
        this.users = users;
    }

    // ---------- employee ----------

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public ReferralResponse refer(String username, ReferralRequest request) {
        User referrer = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Job job = jobs.findById(request.jobId())
                .orElseThrow(() -> new FieldValidationException("jobId", "That job does not exist"));
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new BusinessRuleException("'" + job.getTitle() + "' is not open for referrals");
        }
        String email = request.candidateEmail().trim().toLowerCase(Locale.ROOT);
        if (referrals.existsByJobAndCandidateEmail(job, email)) {
            throw duplicate(job);
        }
        Referral referral = new Referral(job, referrer, request.candidateName().trim(), email,
                blankToNull(request.candidatePhone()), blankToNull(request.resumeUrl()), blankToNull(request.notes()));
        try {
            return ReferralResponse.from(referrals.saveAndFlush(referral));
        } catch (DataIntegrityViolationException ex) { // the same candidate referred at the same moment
            throw duplicate(job);
        }
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<ReferralResponse> myReferrals(String username, ReferralStatus status) {
        User referrer = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return referrals.findByReferrerOrderBySubmittedAtDesc(referrer).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .map(ReferralResponse::from).collect(Collectors.toList());
    }

    // ---------- recruiter ----------

    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional(readOnly = true)
    public List<ReferralResponse> list(Long jobId, ReferralStatus status, String search) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return referrals.findAllByOrderBySubmittedAtDesc().stream()
                .filter(r -> jobId == null || r.getJob().getId().equals(jobId))
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> term.isEmpty()
                        || r.getCandidateName().toLowerCase(Locale.ROOT).contains(term)
                        || r.getCandidateEmail().contains(term)
                        || r.getJob().getTitle().toLowerCase(Locale.ROOT).contains(term)
                        || r.getReferrer().getFullname().toLowerCase(Locale.ROOT).contains(term))
                .map(ReferralResponse::from).collect(Collectors.toList());
    }

    /** Moves a referral to the next stage (or to REJECTED). Only the transitions ReferralStatus allows are accepted. */
    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReferralResponse updateStatus(Long id, String recruiter, String target, String note) {
        ReferralStatus next = parse(target);
        Referral referral = referrals.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Referral not found with id " + id));
        ReferralStatus current = referral.getStatus();
        if (current.isFinal()) {
            throw new InvalidStatusException("This referral is already " + current.name().toLowerCase(Locale.ROOT)
                    + " and cannot be changed");
        }
        if (!current.next().contains(next)) {
            String allowed = current.next().stream().sorted().map(s -> s.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" or "));
            throw new InvalidStatusException("A referral that is " + current.name().toLowerCase(Locale.ROOT)
                    + " can only move to " + allowed + ", not " + next.name().toLowerCase(Locale.ROOT));
        }
        referral.moveTo(next, recruiter, blankToNull(note));
        return ReferralResponse.from(referral);
    }

    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional(readOnly = true)
    public RecruiterDashboard dashboard() {
        List<Referral> all = referrals.findAllByOrderBySubmittedAtDesc();
        Map<ReferralStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(Referral::getStatus, Collectors.counting()));
        Map<Long, List<Referral>> byJob = all.stream().collect(Collectors.groupingBy(r -> r.getJob().getId()));

        List<JobStats> perJob = jobs.findAllByOrderByCreatedAtDesc().stream().map(j -> {
            List<Referral> list = byJob.getOrDefault(j.getId(), List.of());
            long selected = list.stream().filter(r -> r.getStatus() == ReferralStatus.SELECTED).count();
            long rejected = list.stream().filter(r -> r.getStatus() == ReferralStatus.REJECTED).count();
            return new JobStats(j.getId(), j.getTitle(), j.getDepartment(), j.getStatus().name(), list.size(),
                    list.size() - selected - rejected, selected, rejected);
        }).sorted((a, b) -> Long.compare(b.total(), a.total())).collect(Collectors.toList());

        long openJobs = jobs.findByStatusOrderByPublishedAtDesc(JobStatus.PUBLISHED).size();
        List<StatusCount> counts = Arrays.stream(ReferralStatus.values())
                .map(s -> new StatusCount(s.name(), byStatus.getOrDefault(s, 0L))).collect(Collectors.toList());
        return new RecruiterDashboard(openJobs, all.size(), byStatus.getOrDefault(ReferralStatus.SELECTED, 0L), counts, perJob);
    }

    // ---------- helpers ----------

    private ConflictException duplicate(Job job) {
        return new ConflictException("This candidate has already been referred for '" + job.getTitle() + "'");
    }

    private ReferralStatus parse(String value) {
        try {
            return ReferralStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("status", "Unknown status '" + value + "'");
        }
    }

    private String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
