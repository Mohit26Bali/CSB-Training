package com.hackathon.scholarship.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.scholarship.dto.ProgramRequest;
import com.hackathon.scholarship.dto.ProgramResponse;
import com.hackathon.scholarship.entity.Application;
import com.hackathon.scholarship.entity.ApplicationStatus;
import com.hackathon.scholarship.entity.ScholarshipProgram;
import com.hackathon.scholarship.exception.FieldValidationException;
import com.hackathon.scholarship.exception.ResourceNotFoundException;
import com.hackathon.scholarship.repo.ApplicationRepository;
import com.hackathon.scholarship.repo.ScholarshipProgramRepository;

@Service
public class ProgramService {

    private final ScholarshipProgramRepository programs;
    private final ApplicationRepository applications;

    public ProgramService(ScholarshipProgramRepository programs, ApplicationRepository applications) {
        this.programs = programs;
        this.applications = applications;
    }

    /** What a student sees: every active programme, open ones flagged (the deadline may have passed). */
    @Transactional(readOnly = true)
    public List<ProgramResponse> listActive() {
        LocalDate today = LocalDate.now();
        return programs.findByActiveTrueOrderByDeadlineAsc().stream()
                .map(p -> ProgramResponse.from(p, today, 0, 0))
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SCHOLARSHIP_ADMIN')")
    @Transactional(readOnly = true)
    public List<ProgramResponse> listAll() {
        LocalDate today = LocalDate.now();
        Map<Long, List<Application>> byProgram = applications.findAllByOrderBySubmittedAtDesc().stream()
                .collect(Collectors.groupingBy(a -> a.getProgram().getId()));
        return programs.findAllByOrderByDeadlineAsc().stream().map(p -> {
            List<Application> list = byProgram.getOrDefault(p.getId(), List.of());
            long approved = list.stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED).count();
            return ProgramResponse.from(p, today, list.size(), approved);
        }).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SCHOLARSHIP_ADMIN')")
    @Transactional
    public ProgramResponse create(ProgramRequest request) {
        String name = request.name().trim();
        if (programs.existsByNameIgnoreCase(name)) {
            throw new FieldValidationException("name", "A scholarship with this name already exists");
        }
        ScholarshipProgram program = new ScholarshipProgram(name, clean(request.description()), request.amount(),
                request.deadline(), request.minGpa(), request.maxFamilyIncome());
        program.update(name, clean(request.description()), request.amount(), request.deadline(), request.minGpa(),
                request.maxFamilyIncome(), request.active() == null || request.active());
        return ProgramResponse.from(programs.save(program), LocalDate.now(), 0, 0);
    }

    @PreAuthorize("hasRole('SCHOLARSHIP_ADMIN')")
    @Transactional
    public ProgramResponse update(Long id, ProgramRequest request) {
        ScholarshipProgram program = programs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scholarship not found with id " + id));
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(program.getName()) && programs.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new FieldValidationException("name", "A scholarship with this name already exists");
        }
        program.update(name, clean(request.description()), request.amount(), request.deadline(), request.minGpa(),
                request.maxFamilyIncome(), request.active() == null ? program.isActive() : request.active());
        return ProgramResponse.from(program, LocalDate.now(), 0, 0);
    }

    private String clean(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
