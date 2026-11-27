package com.icc.incident.service;

import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ConflictException;
import com.icc.common.error.ForbiddenException;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import com.icc.common.page.PageResponse;
import com.icc.incident.client.AssignmentClient;
import com.icc.incident.dto.AddCommentRequest;
import com.icc.incident.dto.CommentResponse;
import com.icc.incident.dto.CreateIncidentRequest;
import com.icc.incident.dto.HoldRequest;
import com.icc.incident.dto.IncidentDetailResponse;
import com.icc.incident.dto.IncidentResponse;
import com.icc.incident.dto.InternalIncidentResponse;
import com.icc.incident.dto.ProblemClosedRequest;
import com.icc.incident.dto.ReopenRequest;
import com.icc.incident.dto.ResolveRequest;
import com.icc.incident.dto.UpdateIncidentRequest;
import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.HoldReason;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.incident.entity.IncidentState;
import com.icc.incident.itsm.ItsmSettings;
import com.icc.incident.mapper.IncidentMapper;
import com.icc.incident.repository.IncidentCommentRepository;
import com.icc.incident.repository.IncidentRepository;
import com.icc.incident.repository.IncidentScope;
import com.icc.incident.repository.IncidentSpecifications;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The incident life cycle (ARCHITECTURE, business rules):
 * NEW, then IN_PROGRESS (accept), then RESOLVED (needs a resolution), then CLOSED. IN_PROGRESS can also go to
 * ON_HOLD (needs a reason) and back. NEW, IN_PROGRESS and ON_HOLD can be CANCELLED (D32). A RESOLVED incident
 * can be reopened (back to work, D34) and takes comments. CLOSED and CANCELLED are final: no comments, no reopen (D39).
 * Role checks (who may call an endpoint) are on the controller. The rules about the data are here.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository commentRepository;
    private final AssignmentClient assignmentClient;
    private final PriorityCalculator priorityCalculator;
    private final IncidentMapper mapper;
    private final AuditService auditService;
    private final ItsmSettings itsmSettings;

    public IncidentService(IncidentRepository incidentRepository, IncidentCommentRepository commentRepository,
                           AssignmentClient assignmentClient, PriorityCalculator priorityCalculator,
                           IncidentMapper mapper, AuditService auditService, ItsmSettings itsmSettings) {
        this.incidentRepository = incidentRepository;
        this.commentRepository = commentRepository;
        this.assignmentClient = assignmentClient;
        this.priorityCalculator = priorityCalculator;
        this.mapper = mapper;
        this.auditService = auditService;
        this.itsmSettings = itsmSettings;
    }

    @Transactional
    public IncidentResponse create(CreateIncidentRequest request, JwtClaims caller) {
        String category = IncidentCategories.require(request.category());
        int priority = priorityCalculator.calculate(request.impact(), request.urgency());
        Long teamId = resolveTeam(category);

        Incident incident = new Incident(request.shortDescription().trim(), request.description().trim(),
                category, request.impact(), request.urgency(), priority, caller.userId(), teamId);
        incident.setSubcategory(IncidentCategories.requireSubcategory(category, request.subcategory()));
        // Copies for the sync (D38). The team name needs a call, so it is made only when the sync is switched on.
        incident.setReporterEmail(caller.username());
        if (itsmSettings.enabled()) {
            incident.setAssignedTeamName(teamNameOf(teamId));
        }
        // The display number comes from the id, which exists only after the first save.
        // Until then a short unique placeholder is used (the column is 20 characters long).
        incident.setNumber("TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        Incident saved = incidentRepository.save(incident);
        saved.setNumber(String.format("INC%07d", saved.getId()));

        changed(saved);
        auditService.record(caller.userId(), "INCIDENT_CREATED", "INCIDENT", saved.getId(),
                "Priority " + priority + ", category " + category + ", team " + teamId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(String scopeText, String stateText, String text,
                                               int page, int size, JwtClaims caller) {
        IncidentScope scope = IncidentScope.parse(scopeText);
        if (caller.role() == Role.EMPLOYEE && scope != IncidentScope.MINE) {
            throw new ForbiddenException("Employees can only list their own incidents");
        }
        IncidentState state = parseState(stateText);

        Page<Incident> result = incidentRepository.findAll(
                IncidentSpecifications.matching(scope, state, text, caller),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse<>(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse get(Long id, JwtClaims caller) {
        Incident incident = findVisible(id, caller);
        List<IncidentComment> comments = caller.role() == Role.EMPLOYEE
                ? commentRepository.findByIncidentIdAndTypeOrderByIdAsc(id, CommentType.COMMENT)
                : commentRepository.findByIncidentIdOrderByIdAsc(id);
        return new IncidentDetailResponse(mapper.toResponse(incident),
                comments.stream().map(mapper::toComment).toList());
    }

    @Transactional
    public IncidentResponse update(Long id, UpdateIncidentRequest request, JwtClaims caller) {
        Incident incident = find(id);
        if (!incident.getState().isOpen()) {
            throw new ConflictException("Incident " + incident.getNumber() + " is " + incident.getState()
                    + " and can no longer be edited");
        }
        if (request.category() != null) {
            incident.setCategory(IncidentCategories.require(request.category()));
            // A subcategory belongs to one category: when the category changes, an old one that no longer fits is cleared.
            if (incident.getSubcategory() != null && request.subcategory() == null
                    && !IncidentCategories.SUBCATEGORIES.getOrDefault(incident.getCategory(), List.of())
                    .contains(incident.getSubcategory())) {
                incident.setSubcategory(null);
            }
        }
        if (request.subcategory() != null) {
            incident.setSubcategory(IncidentCategories.requireSubcategory(incident.getCategory(), request.subcategory()));
        }
        if (request.impact() != null) {
            incident.setImpact(request.impact());
        }
        if (request.urgency() != null) {
            incident.setUrgency(request.urgency());
        }
        if (request.description() != null) {
            incident.setDescription(request.description().trim());
        }
        // Priority is always recalculated, never typed.
        incident.setPriority(priorityCalculator.calculate(incident.getImpact(), incident.getUrgency()));

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_UPDATED", "INCIDENT", id,
                "Priority is now " + incident.getPriority() + ", category " + incident.getCategory());
        return mapper.toResponse(incident);
    }

    @Transactional
    public CommentResponse addComment(Long id, AddCommentRequest request, JwtClaims caller) {
        Incident incident = findVisible(id, caller);
        // CLOSED and CANCELLED are final (D39): no comments. A RESOLVED incident still takes them (it can be reopened).
        if (incident.getState() == IncidentState.CANCELLED || incident.getState() == IncidentState.CLOSED) {
            throw new ConflictException("Incident " + incident.getNumber() + " is " + incident.getState()
                    + " and takes no comments");
        }
        if (caller.role() == Role.EMPLOYEE && request.type() == CommentType.WORK_NOTE) {
            throw new ForbiddenException("Employees cannot add work notes");
        }
        IncidentComment comment = saveComment(
                new IncidentComment(id, request.type(), request.body().trim(), caller.userId()));
        return mapper.toComment(comment);
    }

    /** NEW to IN_PROGRESS. The engineer must belong to the team the incident is assigned to. */
    @Transactional
    public IncidentResponse accept(Long id, JwtClaims caller) {
        Incident incident = find(id);
        requireState(incident, "accepted", IncidentState.NEW);
        if (!Objects.equals(incident.getAssignedTeamId(), caller.teamId())) {
            throw new ForbiddenException("This incident belongs to another team");
        }
        incident.setState(IncidentState.IN_PROGRESS);
        incident.setAssignedToId(caller.userId());
        incident.setAssignedToEmail(caller.username());

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_ACCEPTED", "INCIDENT", id, null);
        return mapper.toResponse(incident);
    }

    /**
     * IN_PROGRESS to ON_HOLD. Only the engineer who accepted it. A reason is required. When waiting for the caller,
     * a comment telling the caller what is needed is required too, and it is visible to the caller (as in ServiceNow).
     */
    @Transactional
    public IncidentResponse hold(Long id, HoldRequest request, JwtClaims caller) {
        Incident incident = find(id);
        requireState(incident, "put on hold", IncidentState.IN_PROGRESS);
        requireAssignee(incident, caller, "put the incident on hold");
        String comment = request.comment() == null ? "" : request.comment().trim();
        if (request.reason() == HoldReason.AWAITING_CALLER && comment.isEmpty()) {
            throw new BadRequestException("Tell the caller what is needed (a comment is required when waiting for the caller)");
        }
        incident.setState(IncidentState.ON_HOLD);
        incident.setHoldReason(request.reason());
        if (!comment.isEmpty()) {
            CommentType type = request.reason() == HoldReason.AWAITING_CALLER ? CommentType.COMMENT : CommentType.WORK_NOTE;
            saveComment(new IncidentComment(id, type, comment, caller.userId()));
        }

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_HELD", "INCIDENT", id, "Reason " + request.reason());
        return mapper.toResponse(incident);
    }

    /** ON_HOLD to IN_PROGRESS, by the engineer who is working on it. */
    @Transactional
    public IncidentResponse resume(Long id, JwtClaims caller) {
        Incident incident = find(id);
        requireState(incident, "resumed", IncidentState.ON_HOLD);
        requireAssignee(incident, caller, "resume the incident");
        incident.setState(IncidentState.IN_PROGRESS);
        incident.setHoldReason(null);

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_RESUMED", "INCIDENT", id, null);
        return mapper.toResponse(incident);
    }

    /** NEW, IN_PROGRESS or ON_HOLD to CANCELLED, by an engineer of the owning team. A cancelled incident is final. */
    @Transactional
    public IncidentResponse cancel(Long id, JwtClaims caller) {
        Incident incident = find(id);
        if (!incident.getState().isOpen()) {
            throw new ConflictException("Incident " + incident.getNumber() + " is " + incident.getState()
                    + ". Only an open incident can be cancelled");
        }
        if (!Objects.equals(incident.getAssignedTeamId(), caller.teamId())) {
            throw new ForbiddenException("This incident belongs to another team");
        }
        incident.setState(IncidentState.CANCELLED);
        incident.setHoldReason(null);
        incident.setClosedAt(LocalDateTime.now());

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_CANCELLED", "INCIDENT", id, null);
        return mapper.toResponse(incident);
    }

    /** IN_PROGRESS to RESOLVED. Only the engineer who accepted it, and a resolution is required. */
    @Transactional
    public IncidentResponse resolve(Long id, ResolveRequest request, JwtClaims caller) {
        Incident incident = find(id);
        requireState(incident, "resolved", IncidentState.IN_PROGRESS);
        if (!Objects.equals(incident.getAssignedToId(), caller.userId())) {
            throw new ForbiddenException("Only the engineer who accepted the incident can resolve it");
        }
        // Check the code before anything is changed: it must be one of the fixed values (D36).
        String resolutionCode = ResolutionCodes.require(request.resolutionCode());
        incident.setState(IncidentState.RESOLVED);
        incident.setResolutionCode(resolutionCode);
        incident.setResolutionNotes(request.resolutionNotes().trim());
        incident.setResolvedAt(LocalDateTime.now());

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_RESOLVED", "INCIDENT", id,
                "Resolution code " + incident.getResolutionCode());
        return mapper.toResponse(incident);
    }

    /** RESOLVED to CLOSED, by an engineer of the owning team. */
    @Transactional
    public IncidentResponse close(Long id, JwtClaims caller) {
        Incident incident = find(id);
        requireState(incident, "closed", IncidentState.RESOLVED);
        if (!Objects.equals(incident.getAssignedTeamId(), caller.teamId())) {
            throw new ForbiddenException("This incident belongs to another team");
        }
        incident.setState(IncidentState.CLOSED);
        incident.setClosedAt(LocalDateTime.now());

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_CLOSED", "INCIDENT", id, null);
        return mapper.toResponse(incident);
    }

    /**
     * RESOLVED back to work (D34; a CLOSED incident is final, D39). Only the reporter or an engineer of the owning team, and a reason is
     * required. The incident goes back IN_PROGRESS to the engineer who had it, or NEW when nobody did. The reason is
     * saved as a comment, the resolved and closed times are cleared, and the resolution text stays in the record as history.
     */
    @Transactional
    public IncidentResponse reopen(Long id, ReopenRequest request, JwtClaims caller) {
        Incident incident = findVisible(id, caller);   // an employee can reach only their own incident
        IncidentState before = incident.getState();
        if (before != IncidentState.RESOLVED) {
            throw new ConflictException("Incident " + incident.getNumber() + " is " + before
                    + ". Only a RESOLVED incident can be reopened"
                    + (before == IncidentState.CLOSED ? "; a closed incident is final, report a new one" : ""));
        }
        if (caller.role() == Role.SUPPORT_ENGINEER && !Objects.equals(incident.getAssignedTeamId(), caller.teamId())) {
            throw new ForbiddenException("This incident belongs to another team");
        }
        incident.setState(incident.getAssignedToId() == null ? IncidentState.NEW : IncidentState.IN_PROGRESS);
        incident.setResolvedAt(null);
        incident.setClosedAt(null);
        saveComment(new IncidentComment(id, CommentType.COMMENT,
                "Reopened: " + request.reason().trim(), caller.userId()));

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_REOPENED", "INCIDENT", id, "From " + before);
        return mapper.toResponse(incident);
    }

    /**
     * Moves an incident to another team. Allowed in NEW and IN_PROGRESS. The new team has to accept it,
     * so the incident goes back to NEW with nobody assigned.
     */
    @Transactional
    public IncidentResponse reassign(Long id, Long teamId, JwtClaims caller) {
        Incident incident = find(id);
        if (!incident.getState().isOpen()) {
            throw new ConflictException("Incident " + incident.getNumber() + " is " + incident.getState()
                    + " and can no longer be reassigned");
        }
        var teams = assignmentClient.teams();
        boolean teamExists = teams.stream().anyMatch(team -> team.id().equals(teamId));
        if (!teamExists) {
            throw new BadRequestException("Team " + teamId + " does not exist");
        }
        Long oldTeamId = incident.getAssignedTeamId();
        incident.setAssignedTeamId(teamId);
        incident.setAssignedToId(null);
        incident.setAssignedToEmail(null);
        incident.setAssignedTeamName(teams.stream().filter(team -> team.id().equals(teamId)).map(team -> team.name()).findFirst().orElse(null));
        incident.setHoldReason(null);
        incident.setState(IncidentState.NEW);

        changed(incident);
        auditService.record(caller.userId(), "INCIDENT_REASSIGNED", "INCIDENT", id,
                "Team " + oldTeamId + " to team " + teamId);
        return mapper.toResponse(incident);
    }

    // ---- used by the Major Incident, RCA and Dashboard services (/internal) ----

    @Transactional(readOnly = true)
    public InternalIncidentResponse getInternal(Long id) {
        return mapper.toInternal(find(id));
    }

    /**
     * Called by the RCA service when a problem that covers this incident is closed (D32, ServiceNow's "Update Related
     * Incidents"). An incident that is ON_HOLD goes back to IN_PROGRESS, and the problem's close notes are added as a
     * work note. A finished incident is left alone.
     */
    @Transactional
    public InternalIncidentResponse problemClosed(Long id, ProblemClosedRequest request, JwtClaims caller) {
        Incident incident = find(id);
        if (!incident.getState().isOpen()) {
            return mapper.toInternal(incident);
        }
        boolean woken = incident.getState() == IncidentState.ON_HOLD;
        if (woken) {
            incident.setState(IncidentState.IN_PROGRESS);
            incident.setHoldReason(null);
        }
        String notes = request.closeNotes() == null ? "" : request.closeNotes().trim();
        if (!notes.isEmpty()) {
            saveComment(new IncidentComment(id, CommentType.WORK_NOTE,
                    "Related problem " + request.problemNumber() + " closed with the following close notes:\n\n" + notes,
                    caller.userId()));
        }
        changed(incident);
        auditService.record(caller.userId(), "PROBLEM_CLOSED_NOTICE", "INCIDENT", id,
                "Problem " + request.problemNumber() + (woken ? ", incident resumed" : ""));
        return mapper.toInternal(incident);
    }

    /** One incident belongs to at most one major incident. */
    @Transactional
    public void linkMajorIncident(Long id, Long majorIncidentId, JwtClaims caller) {
        Incident incident = find(id);
        if (incident.getMajorIncidentId() != null && !incident.getMajorIncidentId().equals(majorIncidentId)) {
            throw new ConflictException("Incident " + incident.getNumber() + " already has a major incident");
        }
        incident.setMajorIncidentId(majorIncidentId);

        auditService.record(caller.userId(), "MAJOR_INCIDENT_LINKED", "INCIDENT", id,
                "Major incident " + majorIncidentId);
    }

    // ---- helpers ----

    /** The incident changed: when the sync is switched on, it waits for the next sync round (D38). */
    private void changed(Incident incident) {
        if (itsmSettings.enabled()) {
            incident.requestSync();
        }
    }

    /** Saves a comment; when the sync is switched on it waits for the next sync round. */
    private IncidentComment saveComment(IncidentComment comment) {
        if (itsmSettings.enabled()) {
            comment.requestSync();
        }
        return commentRepository.save(comment);
    }

    /** The team's name, only as a copy for the sync: reporting an incident must not fail because of it. */
    private String teamNameOf(Long teamId) {
        try {
            return assignmentClient.teams().stream().filter(team -> team.id().equals(teamId)).map(team -> team.name()).findFirst().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Long resolveTeam(String category) {
        try {
            return assignmentClient.resolve(category).teamId();
        } catch (FeignException.NotFound e) {
            throw new BadRequestException("No team handles category " + category);
        }
    }

    private Incident find(Long id) {
        return incidentRepository.findById(id).orElseThrow(() -> new NotFoundException("Incident", id));
    }

    /** Employees see only their own incidents; for anyone else's it answers "not found". */
    private Incident findVisible(Long id, JwtClaims caller) {
        Incident incident = find(id);
        if (caller.role() == Role.EMPLOYEE && !Objects.equals(incident.getReporterId(), caller.userId())) {
            throw new NotFoundException("Incident", id);
        }
        return incident;
    }

    private void requireAssignee(Incident incident, JwtClaims caller, String action) {
        if (!Objects.equals(incident.getAssignedToId(), caller.userId())) {
            throw new ForbiddenException("Only the engineer working on the incident can " + action);
        }
    }

    private void requireState(Incident incident, String action, IncidentState required) {
        if (incident.getState() != required) {
            throw new ConflictException("Incident " + incident.getNumber() + " is " + incident.getState()
                    + ". It can only be " + action + " from " + required);
        }
    }

    private IncidentState parseState(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return IncidentState.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown state " + text);
        }
    }
}
