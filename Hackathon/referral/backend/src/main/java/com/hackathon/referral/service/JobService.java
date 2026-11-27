package com.hackathon.referral.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.referral.dto.JobRequest;
import com.hackathon.referral.dto.JobResponse;
import com.hackathon.referral.entity.Job;
import com.hackathon.referral.entity.JobStatus;
import com.hackathon.referral.exception.FieldValidationException;
import com.hackathon.referral.exception.InvalidStatusException;
import com.hackathon.referral.exception.ResourceNotFoundException;
import com.hackathon.referral.repo.JobRepository;
import com.hackathon.referral.repo.ReferralRepository;

@Service
public class JobService {

    private final JobRepository jobs;
    private final ReferralRepository referrals;

    public JobService(JobRepository jobs, ReferralRepository referrals) {
        this.jobs = jobs;
        this.referrals = referrals;
    }

    // ---------- everyone signed in ----------

    /** Published jobs only. search matches title, department, location and description; skill matches one skill exactly (ignoring case). */
    @Transactional(readOnly = true)
    public List<JobResponse> listOpen(String search, String skill) {
        String term = lower(search);
        String wantedSkill = lower(skill);
        return jobs.findByStatusOrderByPublishedAtDesc(JobStatus.PUBLISHED).stream()
                .filter(j -> term.isEmpty()
                        || j.getTitle().toLowerCase(Locale.ROOT).contains(term)
                        || j.getDepartment().toLowerCase(Locale.ROOT).contains(term)
                        || j.getLocation().toLowerCase(Locale.ROOT).contains(term)
                        || j.getDescription().toLowerCase(Locale.ROOT).contains(term)
                        || j.getSkillList().stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(term)))
                .filter(j -> wantedSkill.isEmpty() || j.getSkillList().stream().anyMatch(s -> s.equalsIgnoreCase(wantedSkill)))
                .map(j -> JobResponse.from(j, 0))
                .collect(Collectors.toList());
    }

    /** Every skill used by a published job, alphabetically, for the skill filter. */
    @Transactional(readOnly = true)
    public List<String> openSkills() {
        return jobs.findByStatusOrderByPublishedAtDesc(JobStatus.PUBLISHED).stream()
                .flatMap(j -> j.getSkillList().stream())
                .collect(Collectors.toMap(s -> s.toLowerCase(Locale.ROOT), s -> s, (a, b) -> a))
                .values().stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
    }

    // ---------- recruiter ----------

    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional(readOnly = true)
    public List<JobResponse> listAll(JobStatus status) {
        Map<Long, Long> counts = referrals.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getJob().getId(), Collectors.counting()));
        return jobs.findAllByOrderByCreatedAtDesc().stream()
                .filter(j -> status == null || j.getStatus() == status)
                .map(j -> JobResponse.from(j, counts.getOrDefault(j.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional
    public JobResponse create(JobRequest request, String recruiter) {
        Job job = new Job(request.title().trim(), request.department().trim(), request.location().trim(),
                request.description().trim(), cleanSkills(request), recruiter);
        return JobResponse.from(jobs.save(job), 0);
    }

    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional
    public JobResponse update(Long id, JobRequest request) {
        Job job = find(id);
        job.update(request.title().trim(), request.department().trim(), request.location().trim(),
                request.description().trim(), cleanSkills(request));
        return JobResponse.from(job, countFor(job));
    }

    /** DRAFT or CLOSED -> PUBLISHED. */
    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobResponse publish(Long id) {
        Job job = lock(id);
        if (job.getStatus() == JobStatus.PUBLISHED) {
            throw new InvalidStatusException("This job is already published");
        }
        job.publish();
        return JobResponse.from(job, countFor(job));
    }

    /** PUBLISHED -> CLOSED: it stops accepting referrals; existing referrals carry on. */
    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobResponse close(Long id) {
        Job job = lock(id);
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new InvalidStatusException("Only a published job can be closed; this one is "
                    + job.getStatus().name().toLowerCase(Locale.ROOT));
        }
        job.close();
        return JobResponse.from(job, countFor(job));
    }

    // ---------- helpers ----------

    private List<String> cleanSkills(JobRequest request) {
        List<String> skills = request.skills().stream().map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toMap(s -> s.toLowerCase(Locale.ROOT), s -> s, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream().toList();
        if (skills.isEmpty()) {
            throw new FieldValidationException("skills", "Add at least one skill");
        }
        if (skills.stream().anyMatch(s -> s.contains(","))) {
            throw new FieldValidationException("skills", "A skill cannot contain a comma");
        }
        return skills;
    }

    private long countFor(Job job) {
        return referrals.countByJob(job);
    }

    private Job find(Long id) {
        return jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found with id " + id));
    }

    private Job lock(Long id) {
        return jobs.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Job not found with id " + id));
    }

    private String lower(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
