package com.icc.rca.service;

import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ConflictException;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.page.PageResponse;
import com.icc.rca.client.IncidentClient;
import com.icc.rca.client.IncidentClient.IncidentDto;
import com.icc.rca.client.IncidentClient.ProblemClosedBody;
import com.icc.rca.client.MajorIncidentClient;
import com.icc.rca.client.MajorIncidentClient.MajorIncidentDto;
import com.icc.rca.dto.AcceptRiskRequest;
import com.icc.rca.dto.ActionResponse;
import com.icc.rca.dto.AddActionRequest;
import com.icc.rca.dto.AssessRequest;
import com.icc.rca.dto.CreateProblemRequest;
import com.icc.rca.dto.FixRequest;
import com.icc.rca.dto.MarkDuplicateRequest;
import com.icc.rca.dto.ProblemDetailResponse;
import com.icc.rca.dto.ProblemResponse;
import com.icc.rca.dto.ProblemStatsResponse;
import com.icc.rca.dto.UpdateProblemRequest;
import com.icc.rca.entity.ActionStatus;
import com.icc.rca.entity.CorrectiveAction;
import com.icc.rca.entity.Problem;
import com.icc.rca.entity.ProblemAction;
import com.icc.rca.entity.ProblemIncident;
import com.icc.rca.entity.ProblemStatus;
import com.icc.rca.entity.ResolutionCode;
import com.icc.rca.mapper.ProblemMapper;
import com.icc.rca.repository.CorrectiveActionRepository;
import com.icc.rca.repository.ProblemIncidentRepository;
import com.icc.rca.repository.ProblemRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Problem management: a problem groups incidents, goes through six steps and ends with a resolution code (D31).
 * NEW, ASSESS, ROOT_CAUSE_ANALYSIS, FIX_IN_PROGRESS, RESOLVED, CLOSED, the same steps as ServiceNow.
 * Nobody sets the status: each step is an action (assess, confirm, fix, resolve, complete, accept risk,
 * mark duplicate, cancel, re-analyze), and ProblemAction says where each one may be used.
 */
@Service
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

    private final ProblemRepository problemRepository;
    private final CorrectiveActionRepository actionRepository;
    private final ProblemIncidentRepository linkRepository;
    private final IncidentClient incidentClient;
    private final MajorIncidentClient majorIncidentClient;
    private final ProblemMapper mapper;
    private final AuditService auditService;

    public ProblemService(ProblemRepository problemRepository, CorrectiveActionRepository actionRepository,
                          ProblemIncidentRepository linkRepository, IncidentClient incidentClient,
                          MajorIncidentClient majorIncidentClient, ProblemMapper mapper,
                          AuditService auditService) {
        this.problemRepository = problemRepository;
        this.actionRepository = actionRepository;
        this.linkRepository = linkRepository;
        this.incidentClient = incidentClient;
        this.majorIncidentClient = majorIncidentClient;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional
    public ProblemResponse create(CreateProblemRequest request, JwtClaims caller) {
        List<Long> incidentIds = request.incidentIds().stream().distinct().toList();
        // Every referenced record must exist. fetch... fails with a 404 if it does not.
        incidentIds.forEach(this::fetchIncident);
        if (request.majorIncidentId() != null) {
            fetchMajorIncident(request.majorIncidentId());
        }

        Problem problem = new Problem(request.title().trim(), request.description().trim(),
                request.majorIncidentId(), caller.userId());
        problem.setImpactAndUrgency(request.impact() == null ? 3 : request.impact(),
                request.urgency() == null ? 3 : request.urgency());
        // Like ServiceNow: a new problem that already has an assignee goes straight to ASSESS.
        if (request.assignedToId() != null) {
            problem.setAssignedToId(request.assignedToId());
            problem.setStatus(ProblemStatus.ASSESS);
        }
        // A short unique placeholder (the column is 20 characters long) until the id exists.
        problem.setNumber("TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        Problem saved = problemRepository.save(problem);
        saved.setNumber(String.format("PRB%07d", saved.getId()));
        incidentIds.forEach(incidentId -> linkRepository.save(new ProblemIncident(saved.getId(), incidentId)));

        auditService.record(caller.userId(), "PROBLEM_CREATED", "PROBLEM", saved.getId(),
                "Covers " + incidentIds.size() + " incident(s), priority " + saved.getPriority());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProblemResponse> list(String statusText, int page, int size) {
        ProblemStatus status = parseStatus(statusText);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Problem> result = status == null
                ? problemRepository.findAll(pageable)
                : problemRepository.findByStatus(status, pageable);
        return new PageResponse<>(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse get(Long id) {
        Problem problem = find(id);
        List<Long> incidentIds = linkRepository.findByKeyProblemId(id).stream()
                .map(link -> link.getKey().incidentId())
                .sorted()
                .toList();
        List<ActionResponse> actions = actionRepository.findByProblemIdOrderByIdAsc(id).stream()
                .map(mapper::toAction)
                .toList();
        return new ProblemDetailResponse(mapper.toResponse(problem), incidentIds, actions);
    }

    // ------------------------------------------------------------ edit the form

    /** Edits to the form fields. Only the fields that are sent change. A closed problem cannot be edited. */
    @Transactional
    public ProblemResponse update(Long id, UpdateProblemRequest request, JwtClaims caller) {
        Problem problem = find(id);
        if (problem.getStatus() == ProblemStatus.CLOSED) {
            throw new ConflictException("Problem " + problem.getNumber() + " is CLOSED. Use Re-analyze to reopen it");
        }
        if (request.impact() != null || request.urgency() != null) {
            problem.setImpactAndUrgency(request.impact() == null ? problem.getImpact() : request.impact(),
                    request.urgency() == null ? problem.getUrgency() : request.urgency());
        }
        if (request.assignedToId() != null) {
            problem.setAssignedToId(request.assignedToId());
        }
        if (request.causeNotes() != null) {
            problem.setRootCause(requireText(request.causeNotes(), "Cause notes"));
        }
        if (request.fixNotes() != null) {
            problem.setFixNotes(requireText(request.fixNotes(), "Fix notes"));
        }
        if (request.rootCauseCategory() != null) {
            problem.setRootCauseCategory(emptyToNull(request.rootCauseCategory()));
        }
        if (request.workaround() != null) {
            problem.setWorkaround(emptyToNull(request.workaround()));
        }
        auditService.record(caller.userId(), "PROBLEM_UPDATED", "PROBLEM", id, "Priority " + problem.getPriority());
        return mapper.toResponse(problem);
    }

    // ------------------------------------------------------------ the nine actions

    /** NEW to ASSESS. Needs an assignee: sent now, or already on the problem. */
    @Transactional
    public ProblemResponse assess(Long id, AssessRequest request, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.ASSESS);
        if (request != null && request.assignedToId() != null) {
            problem.setAssignedToId(request.assignedToId());
        }
        if (problem.getAssignedToId() == null) {
            throw new ConflictException("Assigned to is required to assess a problem");
        }
        return finish(problem, ProblemAction.ASSESS, ProblemStatus.ASSESS, caller, "Assigned to " + problem.getAssignedToId());
    }

    /** ASSESS to ROOT_CAUSE_ANALYSIS. Nothing else is required. */
    @Transactional
    public ProblemResponse confirm(Long id, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.CONFIRM);
        return finish(problem, ProblemAction.CONFIRM, ProblemStatus.ROOT_CAUSE_ANALYSIS, caller, null);
    }

    /** ROOT_CAUSE_ANALYSIS to FIX_IN_PROGRESS. Both the cause notes and the fix notes are required. */
    @Transactional
    public ProblemResponse fix(Long id, FixRequest request, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.FIX);
        problem.setRootCause(requireText(request.causeNotes(), "Cause notes"));
        problem.setFixNotes(requireText(request.fixNotes(), "Fix notes"));
        if (request.rootCauseCategory() != null) {
            problem.setRootCauseCategory(emptyToNull(request.rootCauseCategory()));
        }
        if (request.workaround() != null) {
            problem.setWorkaround(emptyToNull(request.workaround()));
        }
        return finish(problem, ProblemAction.FIX, ProblemStatus.FIX_IN_PROGRESS, caller, null);
    }

    /** FIX_IN_PROGRESS to RESOLVED, with the code FIX_APPLIED. The fix notes must be there. */
    @Transactional
    public ProblemResponse resolve(Long id, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.RESOLVE);
        if (!problem.hasFixNotes()) {
            throw new ConflictException("Fix notes are required to resolve a problem");
        }
        problem.setResolutionCode(ResolutionCode.FIX_APPLIED);
        problem.setResolvedAt(LocalDateTime.now());
        return finish(problem, ProblemAction.RESOLVE, ProblemStatus.RESOLVED, caller, null);
    }

    /** RESOLVED to CLOSED. */
    @Transactional
    public ProblemResponse complete(Long id, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.COMPLETE);
        return close(problem, ProblemAction.COMPLETE, caller);
    }

    /** Closes the problem with RISK_ACCEPTED. When the workaround was applied, the workaround text is required. */
    @Transactional
    public ProblemResponse acceptRisk(Long id, AcceptRiskRequest request, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.ACCEPT_RISK);
        boolean applied = request != null && request.workaroundApplied();
        if (request != null && request.workaround() != null) {
            problem.setWorkaround(emptyToNull(request.workaround()));
        }
        if (applied && !problem.hasWorkaround()) {
            throw new ConflictException("Describe the workaround when it has been applied");
        }
        problem.setWorkaroundApplied(applied);
        problem.setResolutionCode(ResolutionCode.RISK_ACCEPTED);
        problem.setResolvedAt(LocalDateTime.now());
        return close(problem, ProblemAction.ACCEPT_RISK, caller);
    }

    /**
     * Closes the problem as a copy of another one. The original must exist, must not be this problem and must not
     * be a duplicate itself. The incidents linked here are linked to the original too (ServiceNow copies the related records).
     */
    @Transactional
    public ProblemResponse markDuplicate(Long id, MarkDuplicateRequest request, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.MARK_DUPLICATE);
        if (request.duplicateOfId().equals(id)) {
            throw new BadRequestException("A problem cannot be a duplicate of itself");
        }
        Problem original = find(request.duplicateOfId());
        if (original.getResolutionCode() == ResolutionCode.DUPLICATE) {
            throw new ConflictException("Problem " + original.getNumber() + " is itself a duplicate. Choose the original");
        }

        Set<Long> already = new HashSet<>();
        linkRepository.findByKeyProblemId(original.getId()).forEach(link -> already.add(link.getKey().incidentId()));
        for (ProblemIncident link : linkRepository.findByKeyProblemId(id)) {
            if (!already.contains(link.getKey().incidentId())) {
                linkRepository.save(new ProblemIncident(original.getId(), link.getKey().incidentId()));
            }
        }

        problem.setDuplicateOfId(original.getId());
        problem.setResolutionCode(ResolutionCode.DUPLICATE);
        return close(problem, ProblemAction.MARK_DUPLICATE, caller);
    }

    /** Closes the problem with CANCELED. */
    @Transactional
    public ProblemResponse cancel(Long id, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.CANCEL);
        problem.setResolutionCode(ResolutionCode.CANCELED);
        return close(problem, ProblemAction.CANCEL, caller);
    }

    /**
     * Back to ROOT_CAUSE_ANALYSIS. From RESOLVED or CLOSED the ending is undone: times and resolution code are
     * cleared and the reopen count goes up. The cause and fix notes are kept (ServiceNow does the same).
     */
    @Transactional
    public ProblemResponse reAnalyze(Long id, JwtClaims caller) {
        Problem problem = startAction(id, ProblemAction.RE_ANALYZE);
        if (problem.getStatus() == ProblemStatus.RESOLVED || problem.getStatus() == ProblemStatus.CLOSED) {
            problem.setResolvedAt(null);
            problem.setClosedAt(null);
            problem.setResolutionCode(null);
            problem.setDuplicateOfId(null);
            problem.setWorkaroundApplied(false);
            problem.countReopen();
        }
        return finish(problem, ProblemAction.RE_ANALYZE, ProblemStatus.ROOT_CAUSE_ANALYSIS, caller, null);
    }

    // --------------------------------------------------------- corrective actions

    @Transactional
    public ActionResponse addAction(Long id, AddActionRequest request, JwtClaims caller) {
        Problem problem = find(id);
        if (problem.getStatus() == ProblemStatus.CLOSED) {
            throw new ConflictException("Problem " + problem.getNumber() + " is CLOSED. No more actions can be added");
        }
        CorrectiveAction action = actionRepository.save(new CorrectiveAction(id,
                request.description().trim(), request.ownerId(), request.dueDate()));

        auditService.record(caller.userId(), "ACTION_ADDED", "PROBLEM", id,
                "Owner " + request.ownerId() + ", due " + request.dueDate());
        return mapper.toAction(action);
    }

    @Transactional
    public ActionResponse updateAction(Long problemId, Long actionId, ActionStatus status, JwtClaims caller) {
        find(problemId);
        CorrectiveAction action = actionRepository.findById(actionId)
                .filter(found -> found.getProblemId().equals(problemId))
                .orElseThrow(() -> new NotFoundException("Corrective action", actionId));
        action.setStatus(status);

        auditService.record(caller.userId(), "ACTION_STATUS_CHANGED", "PROBLEM", problemId,
                "Action " + actionId + " is " + status);
        return mapper.toAction(action);
    }

    @Transactional(readOnly = true)
    public ProblemStatsResponse stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : problemRepository.countGroupedByStatus()) {
            byStatus.put(((ProblemStatus) row[0]).name(), (Long) row[1]);
        }
        long open = byStatus.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(ProblemStatus.CLOSED.name()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        return new ProblemStatsResponse(problemRepository.count(), open,
                actionRepository.countByStatus(ActionStatus.OPEN), byStatus);
    }

    // ---- helpers ----

    private Problem find(Long id) {
        return problemRepository.findById(id).orElseThrow(() -> new NotFoundException("Problem", id));
    }

    /** Loads the problem and refuses the action when the problem is in the wrong status. */
    private Problem startAction(Long id, ProblemAction action) {
        Problem problem = find(id);
        if (!action.isAllowedIn(problem.getStatus())) {
            throw new ConflictException("Problem " + problem.getNumber() + " is " + problem.getStatus() + ". "
                    + action + " is not allowed now. Allowed: " + ProblemAction.availableIn(problem.getStatus()));
        }
        return problem;
    }

    /** Moves to the new status and writes the audit line. */
    private ProblemResponse finish(Problem problem, ProblemAction action, ProblemStatus target, JwtClaims caller, String extra) {
        ProblemStatus from = problem.getStatus();
        problem.setStatus(target);
        auditService.record(caller.userId(), "PROBLEM_" + action.name(), "PROBLEM", problem.getId(),
                from + " to " + target + (extra == null ? "" : ". " + extra));
        return mapper.toResponse(problem);
    }

    /** Every ending: sets the closed time, and (like ServiceNow's cascade) marks the problem's open actions DONE. */
    private ProblemResponse close(Problem problem, ProblemAction action, JwtClaims caller) {
        problem.setClosedAt(LocalDateTime.now());
        int closed = 0;
        for (CorrectiveAction corrective : actionRepository.findByProblemIdOrderByIdAsc(problem.getId())) {
            if (corrective.getStatus() == ActionStatus.OPEN) {
                corrective.setStatus(ActionStatus.DONE);
                closed++;
            }
        }
        String code = problem.getResolutionCode() == null ? "" : "Code " + problem.getResolutionCode() + ". ";
        String woken = notifyIncidents(problem);
        return finish(problem, action, ProblemStatus.CLOSED, caller,
                code + closed + " open action(s) marked DONE" + woken);
    }

    /**
     * Like ServiceNow's "Update Related Incidents": tells every incident the problem covers that it was closed, so
     * an incident waiting on hold goes back to work. A duplicate does not notify, because its incidents are now
     * linked to the original problem, which is still open. A failed call never stops the closing: the problem is
     * already decided, so the failure is written to the log and the audit line, and the others are still told.
     */
    private String notifyIncidents(Problem problem) {
        if (problem.getResolutionCode() == ResolutionCode.DUPLICATE) {
            return "";
        }
        ProblemClosedBody body = new ProblemClosedBody(problem.getNumber(), problem.getFixNotes());
        int told = 0;
        int failed = 0;
        for (ProblemIncident link : linkRepository.findByKeyProblemId(problem.getId())) {
            try {
                incidentClient.problemClosed(link.getKey().incidentId(), body);
                told++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not tell incident {} that problem {} was closed: {}",
                        link.getKey().incidentId(), problem.getNumber(), e.getMessage());
            }
        }
        return ". " + told + " incident(s) told" + (failed == 0 ? "" : ", " + failed + " failed");
    }

    private String requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException(field + " are required");
        }
        return text.trim();
    }

    private String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private IncidentDto fetchIncident(Long incidentId) {
        try {
            return incidentClient.get(incidentId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Incident", incidentId);
        }
    }

    private MajorIncidentDto fetchMajorIncident(Long majorIncidentId) {
        try {
            return majorIncidentClient.get(majorIncidentId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Major incident", majorIncidentId);
        }
    }

    private ProblemStatus parseStatus(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return ProblemStatus.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status " + text);
        }
    }
}
