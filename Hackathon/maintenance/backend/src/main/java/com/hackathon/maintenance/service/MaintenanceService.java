package com.hackathon.maintenance.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.maintenance.dto.CategoryStats;
import com.hackathon.maintenance.dto.Dashboard;
import com.hackathon.maintenance.dto.NewRequest;
import com.hackathon.maintenance.dto.RequestResponse;
import com.hackathon.maintenance.dto.TechnicianBody;
import com.hackathon.maintenance.dto.TechnicianResponse;
import com.hackathon.maintenance.entity.Category;
import com.hackathon.maintenance.entity.MaintenanceRequest;
import com.hackathon.maintenance.entity.Priority;
import com.hackathon.maintenance.entity.RequestStatus;
import com.hackathon.maintenance.entity.Technician;
import com.hackathon.maintenance.entity.User;
import com.hackathon.maintenance.exception.BusinessRuleException;
import com.hackathon.maintenance.exception.FieldValidationException;
import com.hackathon.maintenance.exception.ForbiddenActionException;
import com.hackathon.maintenance.exception.InvalidStatusException;
import com.hackathon.maintenance.exception.ResourceNotFoundException;
import com.hackathon.maintenance.repo.MaintenanceRequestRepository;
import com.hackathon.maintenance.repo.TechnicianRepository;
import com.hackathon.maintenance.repo.UserRepository;

/**
 * Maintenance requests, technician assignment, the status flow and the manager's dashboard. Every change to a request
 * first takes its row lock (under READ_COMMITTED), so two managers cannot change the same request at once.
 */
@Service
public class MaintenanceService {

    private final MaintenanceRequestRepository requests;
    private final TechnicianRepository technicians;
    private final UserRepository users;

    public MaintenanceService(MaintenanceRequestRepository requests, TechnicianRepository technicians, UserRepository users) {
        this.requests = requests;
        this.technicians = technicians;
        this.users = users;
    }

    // ---------- resident ----------

    @PreAuthorize("hasRole('RESIDENT')")
    @Transactional
    public RequestResponse raise(String username, NewRequest body) {
        User resident = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Category category = parse(Category.class, body.category(), "category");
        Priority priority = parse(Priority.class, body.priority(), "priority");
        MaintenanceRequest saved = requests.save(new MaintenanceRequest(category, body.description().trim(), body.location().trim(), priority, resident));
        return RequestResponse.from(saved, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('RESIDENT')")
    @Transactional(readOnly = true)
    public List<RequestResponse> mine(String username, RequestStatus status, String search) {
        User resident = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        LocalDateTime now = LocalDateTime.now();
        return requests.findByResidentOrderByCreatedAtDesc(resident).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> matches(r, search))
                .map(r -> RequestResponse.from(r, now)).collect(Collectors.toList());
    }

    /** One request. Residents can only open their own. */
    @Transactional(readOnly = true)
    public RequestResponse detail(Long id, String username, boolean manager) {
        MaintenanceRequest request = requests.findWithPeopleById(id).orElseThrow(() -> notFound(id));
        if (!manager && !request.getResident().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only view your own requests");
        }
        return RequestResponse.from(request, LocalDateTime.now());
    }

    public Map<String, Object> meta() {
        return Map.of("categories", Arrays.stream(Category.values()).map(Enum::name).toList(),
                "priorities", Arrays.stream(Priority.values()).map(Enum::name).toList());
    }

    // ---------- facility manager ----------

    /** Active requests first, the most urgent on top and the oldest first within a priority; finished ones follow, newest first. */
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    @Transactional(readOnly = true)
    public List<RequestResponse> queue(RequestStatus status, Priority priority, Category category, Long technicianId, String aging, String search) {
        LocalDateTime now = LocalDateTime.now();
        return requests.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> priority == null || r.getPriority() == priority)
                .filter(r -> category == null || r.getCategory() == category)
                .filter(r -> technicianId == null || (r.getTechnician() != null && r.getTechnician().getId().equals(technicianId)))
                .filter(r -> aging == null || aging.isBlank() || aging.equalsIgnoreCase(String.valueOf(RequestResponse.agingOf(r, now))))
                .filter(r -> matches(r, search))
                .sorted(Comparator.comparing((MaintenanceRequest r) -> !r.getStatus().isActive())
                        .thenComparing(r -> r.getStatus().isActive() ? -r.getPriority().ordinal() : 0)
                        .thenComparing(r -> r.getStatus().isActive() ? r.getCreatedAt() : LocalDateTime.MAX)
                        .thenComparing(MaintenanceRequest::getCreatedAt, Comparator.reverseOrder()))
                .map(r -> RequestResponse.from(r, now)).collect(Collectors.toList());
    }

    /** Assigns (or reassigns) a technician. An open request becomes ASSIGNED. */
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse assign(Long id, Long technicianId) {
        MaintenanceRequest request = requests.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!request.getStatus().isActive()) {
            throw new InvalidStatusException("A " + label(request.getStatus()) + " request cannot be assigned");
        }
        Technician technician = technicians.findById(technicianId)
                .orElseThrow(() -> new FieldValidationException("technicianId", "That technician does not exist"));
        if (!technician.isActive()) {
            throw new BusinessRuleException(technician.getName() + " is not available for new work");
        }
        request.assignTo(technician);
        return RequestResponse.from(request, LocalDateTime.now());
    }

    /** ASSIGNED to IN_PROGRESS to RESOLVED to CLOSED. Resolving needs notes; ASSIGNED itself only comes from assigning. */
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse updateStatus(Long id, String target, String notes) {
        MaintenanceRequest request = requests.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        RequestStatus next = parse(RequestStatus.class, target, "status");
        RequestStatus current = request.getStatus();
        if (current == RequestStatus.CLOSED) {
            throw new InvalidStatusException("This request is closed and cannot be changed");
        }
        if (next == RequestStatus.ASSIGNED) {
            throw new BusinessRuleException("Assign a technician to move a request to assigned");
        }
        if (!current.next().contains(next)) {
            String allowed = current == RequestStatus.OPEN ? "assigned (by choosing a technician)"
                    : current.next().stream().map(this::label).collect(Collectors.joining(" or "));
            throw new InvalidStatusException("A request that is " + label(current) + " can only move to " + allowed + ", not " + label(next));
        }
        String text = notes == null || notes.isBlank() ? null : notes.trim();
        if (next == RequestStatus.RESOLVED && text == null) {
            throw new FieldValidationException("notes", "Resolution notes are required when resolving a request");
        }
        request.moveTo(next, text);
        return RequestResponse.from(request, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    @Transactional(readOnly = true)
    public List<TechnicianResponse> technicians() {
        Map<Long, Long> load = activeJobs();
        return technicians.findAllByOrderByNameAsc().stream().map(t -> TechnicianResponse.from(t, load.getOrDefault(t.getId(), 0L))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    @Transactional
    public TechnicianResponse addTechnician(TechnicianBody body) {
        String name = body.name().trim();
        if (technicians.existsByNameIgnoreCase(name)) {
            throw new FieldValidationException("name", name + " is already on the list");
        }
        Category trade = parse(Category.class, body.trade(), "trade");
        try {
            Technician saved = technicians.saveAndFlush(new Technician(name, trade, body.phone() == null || body.phone().isBlank() ? null : body.phone().trim()));
            return TechnicianResponse.from(saved, 0);
        } catch (DataIntegrityViolationException ex) { // added at the same moment
            throw new FieldValidationException("name", name + " is already on the list");
        }
    }

    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    @Transactional
    public TechnicianResponse setActive(Long id, boolean active) {
        Technician technician = technicians.findById(id).orElseThrow(() -> new ResourceNotFoundException("Technician not found with id " + id));
        long jobs = activeJobs().getOrDefault(id, 0L);
        if (!active && jobs > 0) {
            throw new BusinessRuleException(technician.getName() + " still has " + jobs + " active job" + (jobs == 1 ? "" : "s") + ". Reassign them first.");
        }
        technician.setActive(active);
        return TechnicianResponse.from(technician, jobs);
    }

    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<MaintenanceRequest> all = requests.findAllByOrderByCreatedAtDesc();
        LocalDateTime now = LocalDateTime.now();
        Map<RequestStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(MaintenanceRequest::getStatus, Collectors.counting()));
        Map<String, Long> aging = all.stream().map(r -> RequestResponse.agingOf(r, now)).filter(a -> a != null)
                .collect(Collectors.groupingBy(a -> a, Collectors.counting()));
        double hours = all.stream().filter(r -> r.getResolvedAt() != null)
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getResolvedAt()).toMinutes()).average().orElse(0) / 60.0;
        Map<Category, List<MaintenanceRequest>> byCategory = all.stream().collect(Collectors.groupingBy(MaintenanceRequest::getCategory));
        List<CategoryStats> categories = Arrays.stream(Category.values()).map(c -> {
            List<MaintenanceRequest> list = byCategory.getOrDefault(c, List.of());
            return new CategoryStats(c.name(), list.size(), list.stream().filter(r -> r.getStatus().isActive()).count(),
                    list.stream().filter(r -> !r.getStatus().isActive()).count());
        }).collect(Collectors.toList());
        return new Dashboard(byStatus.getOrDefault(RequestStatus.OPEN, 0L), byStatus.getOrDefault(RequestStatus.ASSIGNED, 0L),
                byStatus.getOrDefault(RequestStatus.IN_PROGRESS, 0L), byStatus.getOrDefault(RequestStatus.RESOLVED, 0L),
                byStatus.getOrDefault(RequestStatus.CLOSED, 0L), aging.getOrDefault("FRESH", 0L), aging.getOrDefault("AGING", 0L),
                aging.getOrDefault("STALE", 0L),
                all.stream().filter(r -> r.getStatus().isActive() && r.getPriority() == Priority.EMERGENCY).count(),
                Math.round(hours * 10) / 10.0, categories, technicians());
    }

    // ---------- helpers ----------

    private Map<Long, Long> activeJobs() {
        return requests.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getStatus().isActive() && r.getTechnician() != null)
                .collect(Collectors.groupingBy(r -> r.getTechnician().getId(), Collectors.counting()));
    }

    private String label(RequestStatus s) {
        return s.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean matches(MaintenanceRequest r, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return r.getLocation().toLowerCase(Locale.ROOT).contains(term) || r.getDescription().toLowerCase(Locale.ROOT).contains(term)
                || RequestResponse.numberOf(r.getId()).toLowerCase(Locale.ROOT).contains(term)
                || r.getResident().getFullname().toLowerCase(Locale.ROOT).contains(term);
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Request not found with id " + id);
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException(field, "Unknown " + field + " '" + value + "'");
        }
    }
}
