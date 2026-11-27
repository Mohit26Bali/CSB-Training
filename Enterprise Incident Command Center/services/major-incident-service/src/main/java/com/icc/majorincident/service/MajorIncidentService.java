package com.icc.majorincident.service;

import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ConflictException;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.page.PageResponse;
import com.icc.majorincident.client.IncidentClient;
import com.icc.majorincident.client.IncidentClient.IncidentDto;
import com.icc.majorincident.client.IncidentClient.LinkRequest;
import com.icc.majorincident.dto.DeclareMajorIncidentRequest;
import com.icc.majorincident.dto.InternalMajorIncidentResponse;
import com.icc.majorincident.dto.MajorIncidentDetailResponse;
import com.icc.majorincident.dto.MajorIncidentResponse;
import com.icc.majorincident.dto.MajorIncidentStatsResponse;
import com.icc.majorincident.dto.TimelineEntryResponse;
import com.icc.majorincident.entity.MajorIncident;
import com.icc.majorincident.entity.MajorIncidentStatus;
import com.icc.majorincident.entity.TimelineEntry;
import com.icc.majorincident.entity.TimelineEntryType;
import com.icc.majorincident.mapper.MajorIncidentMapper;
import com.icc.majorincident.repository.MajorIncidentRepository;
import com.icc.majorincident.repository.TimelineEntryRepository;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escalating a priority 1 incident into a major incident, and running it:
 * DECLARED, MITIGATING, RESOLVED, CLOSED, with a timeline of what happened.
 */
@Service
public class MajorIncidentService {

    private static final int CRITICAL = 1;

    private final MajorIncidentRepository repository;
    private final TimelineEntryRepository timelineRepository;
    private final IncidentClient incidentClient;
    private final MajorIncidentMapper mapper;
    private final AuditService auditService;

    public MajorIncidentService(MajorIncidentRepository repository, TimelineEntryRepository timelineRepository,
                                IncidentClient incidentClient, MajorIncidentMapper mapper,
                                AuditService auditService) {
        this.repository = repository;
        this.timelineRepository = timelineRepository;
        this.incidentClient = incidentClient;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    /**
     * Asks the Incident service to confirm the incident is priority 1, saves the major incident,
     * then tells the Incident service to store the link (ARCHITECTURE, escalate flow).
     * If the link call fails, the exception rolls back our own save, so nothing is half done.
     */
    @Transactional
    public MajorIncidentResponse declare(DeclareMajorIncidentRequest request, JwtClaims caller) {
        IncidentDto incident = fetchIncident(request.incidentId());
        if (incident.priority() != CRITICAL) {
            throw new ConflictException("Only a priority 1 incident can be escalated. "
                    + incident.number() + " has priority " + incident.priority());
        }
        if (incident.majorIncidentId() != null || repository.existsByIncidentId(request.incidentId())) {
            throw new ConflictException("Incident " + incident.number() + " already has a major incident");
        }

        Long commanderId = request.commanderId() != null ? request.commanderId() : caller.userId();
        MajorIncident saved = repository.save(new MajorIncident(request.incidentId(),
                request.summary().trim(), request.impactSummary().trim(), commanderId, caller.userId()));
        timelineRepository.save(new TimelineEntry(saved.getId(), TimelineEntryType.STATUS_CHANGE,
                "Major incident declared for " + incident.number(), caller.userId()));

        incidentClient.linkMajorIncident(request.incidentId(), new LinkRequest(saved.getId()));

        auditService.record(caller.userId(), "MAJOR_INCIDENT_DECLARED", "MAJOR_INCIDENT", saved.getId(),
                "Incident " + incident.number() + ", commander " + commanderId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<MajorIncidentResponse> list(String statusText, int page, int size) {
        MajorIncidentStatus status = parseStatus(statusText);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<MajorIncident> result = status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return new PageResponse<>(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public MajorIncidentDetailResponse get(Long id) {
        MajorIncident majorIncident = find(id);
        return new MajorIncidentDetailResponse(mapper.toResponse(majorIncident), timeline(id));
    }

    @Transactional
    public TimelineEntryResponse addTimelineEntry(Long id, String text, JwtClaims caller) {
        MajorIncident majorIncident = find(id);
        if (majorIncident.getStatus() == MajorIncidentStatus.CLOSED) {
            throw new ConflictException("Major incident " + id + " is CLOSED. No more updates can be added");
        }
        TimelineEntry entry = timelineRepository.save(
                new TimelineEntry(id, TimelineEntryType.UPDATE, text.trim(), caller.userId()));
        return mapper.toEntry(entry);
    }

    /** Only the next status in the order is allowed. The change is also written to the timeline. */
    @Transactional
    public MajorIncidentResponse changeStatus(Long id, MajorIncidentStatus requested, JwtClaims caller) {
        MajorIncident majorIncident = find(id);
        MajorIncidentStatus current = majorIncident.getStatus();
        if (requested != current.next()) {
            String expected = current.next() == null ? "none (it is already the last status)" : current.next().name();
            throw new ConflictException("Major incident " + id + " is " + current
                    + ". The only allowed next status is " + expected);
        }

        majorIncident.setStatus(requested);
        if (requested == MajorIncidentStatus.RESOLVED) {
            majorIncident.setResolvedAt(LocalDateTime.now());
        }
        if (requested == MajorIncidentStatus.CLOSED) {
            majorIncident.setClosedAt(LocalDateTime.now());
        }
        timelineRepository.save(new TimelineEntry(id, TimelineEntryType.STATUS_CHANGE,
                "Status changed from " + current + " to " + requested, caller.userId()));

        auditService.record(caller.userId(), "MAJOR_INCIDENT_STATUS_CHANGED", "MAJOR_INCIDENT", id,
                current + " to " + requested);
        return mapper.toResponse(majorIncident);
    }

    // ---- used by the RCA and Dashboard services (/internal) ----

    @Transactional(readOnly = true)
    public InternalMajorIncidentResponse getInternal(Long id) {
        return mapper.toInternal(find(id));
    }

    @Transactional(readOnly = true)
    public MajorIncidentStatsResponse stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : repository.countGroupedByStatus()) {
            byStatus.put(((MajorIncidentStatus) row[0]).name(), (Long) row[1]);
        }
        long active = byStatus.entrySet().stream()
                .filter(entry -> MajorIncidentStatus.valueOf(entry.getKey()).isActive())
                .mapToLong(Map.Entry::getValue)
                .sum();
        return new MajorIncidentStatsResponse(repository.count(), active, byStatus);
    }

    // ---- helpers ----

    private MajorIncident find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Major incident", id));
    }

    private IncidentDto fetchIncident(Long incidentId) {
        try {
            return incidentClient.get(incidentId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Incident", incidentId);
        }
    }

    private java.util.List<TimelineEntryResponse> timeline(Long id) {
        return timelineRepository.findByMajorIncidentIdOrderByIdAsc(id).stream().map(mapper::toEntry).toList();
    }

    private MajorIncidentStatus parseStatus(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MajorIncidentStatus.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status " + text);
        }
    }
}
