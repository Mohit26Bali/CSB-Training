package com.hackathon.complaints.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.complaints.dto.AgentResponse;
import com.hackathon.complaints.dto.ComplaintDetail;
import com.hackathon.complaints.dto.ComplaintRequest;
import com.hackathon.complaints.dto.ComplaintResponse;
import com.hackathon.complaints.dto.Count;
import com.hackathon.complaints.dto.DayCount;
import com.hackathon.complaints.dto.EventResponse;
import com.hackathon.complaints.dto.Statistics;
import com.hackathon.complaints.entity.Category;
import com.hackathon.complaints.entity.Complaint;
import com.hackathon.complaints.entity.ComplaintEvent;
import com.hackathon.complaints.entity.ComplaintStatus;
import com.hackathon.complaints.entity.EventType;
import com.hackathon.complaints.entity.Priority;
import com.hackathon.complaints.entity.Role;
import com.hackathon.complaints.entity.User;
import com.hackathon.complaints.exception.BusinessRuleException;
import com.hackathon.complaints.exception.ConflictException;
import com.hackathon.complaints.exception.FieldValidationException;
import com.hackathon.complaints.exception.ForbiddenActionException;
import com.hackathon.complaints.exception.InvalidStatusException;
import com.hackathon.complaints.exception.ResourceNotFoundException;
import com.hackathon.complaints.repo.ComplaintEventRepository;
import com.hackathon.complaints.repo.ComplaintRepository;
import com.hackathon.complaints.repo.UserRepository;

/**
 * Complaints, the agents' queue, the status flow, the timeline, customer feedback and statistics. Every change to a
 * complaint takes its row lock first (under READ_COMMITTED), so two agents cannot resolve or reassign the same one at once.
 */
@Service
public class ComplaintService {

    private final ComplaintRepository complaints;
    private final ComplaintEventRepository events;
    private final UserRepository users;

    public ComplaintService(ComplaintRepository complaints, ComplaintEventRepository events, UserRepository users) {
        this.complaints = complaints;
        this.events = events;
        this.users = users;
    }

    // ---------- customer ----------

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ComplaintResponse file(String username, ComplaintRequest request) {
        User customer = findUser(username);
        Category category = parse(Category.class, request.category(), "category");
        Priority priority = parse(Priority.class, request.priority(), "priority");
        Complaint saved = complaints.save(new Complaint(request.subject().trim(), request.description().trim(), category, priority, customer));
        events.save(new ComplaintEvent(saved, customer, EventType.FILED, ComplaintStatus.OPEN, null));
        return ComplaintResponse.from(saved, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<ComplaintResponse> mine(String username, ComplaintStatus status, String search) {
        User customer = findUser(username);
        LocalDateTime now = LocalDateTime.now();
        return complaints.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> matches(c, search))
                .map(c -> ComplaintResponse.from(c, now)).collect(Collectors.toList());
    }

    /** A complaint with its timeline. Customers can only open their own. */
    @Transactional(readOnly = true)
    public ComplaintDetail detail(Long id, String username, boolean agent) {
        Complaint complaint = complaints.findWithPeopleById(id).orElseThrow(() -> notFound(id));
        if (!agent && !complaint.getCustomer().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only view your own complaints");
        }
        List<EventResponse> timeline = events.findByComplaintOrderByCreatedAtAscIdAsc(complaint).stream().map(EventResponse::from).collect(Collectors.toList());
        return new ComplaintDetail(ComplaintResponse.from(complaint, LocalDateTime.now()), timeline);
    }

    /** The customer rates how it was handled, once, after it is resolved. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ComplaintResponse feedback(Long id, String username, int rating, String comment) {
        Complaint complaint = complaints.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!complaint.getCustomer().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only rate your own complaints");
        }
        if (complaint.getStatus().isActive()) {
            throw new InvalidStatusException("You can rate a complaint once it has been resolved");
        }
        if (complaint.getRating() != null) {
            throw new ConflictException("You have already rated this complaint");
        }
        String text = comment == null || comment.isBlank() ? null : comment.trim();
        complaint.rate(rating, text);
        events.save(new ComplaintEvent(complaint, findUser(username), EventType.FEEDBACK, null, rating + " of 5" + (text == null ? "" : ": " + text)));
        return ComplaintResponse.from(complaint, LocalDateTime.now());
    }

    public Map<String, Object> meta() {
        return Map.of("categories", Arrays.stream(Category.values()).map(Enum::name).toList(),
                "priorities", Arrays.stream(Priority.values()).map(Enum::name).toList());
    }

    // ---------- agent ----------

    /** The queue: active complaints first, the most urgent on top and the longest waiting first within a priority. */
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(readOnly = true)
    public List<ComplaintResponse> queue(String agentUsername, ComplaintStatus status, Priority priority, Category category,
                                         String assignee, String search) {
        LocalDateTime now = LocalDateTime.now();
        return complaints.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> priority == null || c.getPriority() == priority)
                .filter(c -> category == null || c.getCategory() == category)
                .filter(c -> assignee == null || assignee.isBlank() || assigneeMatches(c, assignee, agentUsername))
                .filter(c -> matches(c, search))
                .sorted(Comparator.comparing((Complaint c) -> !c.getStatus().isActive())
                        .thenComparing(c -> c.getStatus().isActive() ? c.getPriority().ordinal() * -1 : 0)
                        .thenComparing(c -> c.getStatus().isActive() ? c.getCreatedAt() : LocalDateTime.MAX)
                        .thenComparing(Complaint::getCreatedAt, Comparator.reverseOrder()))
                .map(c -> ComplaintResponse.from(c, now)).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ComplaintResponse assign(Long id, String assigneeUsername, String actorUsername) {
        Complaint complaint = complaints.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!complaint.getStatus().isActive()) {
            throw new InvalidStatusException("A " + complaint.getStatus().name().toLowerCase(Locale.ROOT) + " complaint cannot be reassigned");
        }
        User actor = findUser(actorUsername);
        if (assigneeUsername == null || assigneeUsername.isBlank()) {
            if (complaint.getStatus() != ComplaintStatus.OPEN) {
                throw new BusinessRuleException("Work has started on this complaint, so it needs an owner. Reassign it instead.");
            }
            complaint.assignTo(null);
            events.save(new ComplaintEvent(complaint, actor, EventType.ASSIGNED, null, "Put back in the unassigned queue"));
        } else {
            User agent = users.findByUsername(assigneeUsername.trim()).filter(u -> u.getRole() == Role.SUPPORT_AGENT)
                    .orElseThrow(() -> new FieldValidationException("assignee", "'" + assigneeUsername + "' is not a support agent"));
            complaint.assignTo(agent);
            events.save(new ComplaintEvent(complaint, actor, EventType.ASSIGNED, null, "Assigned to " + agent.getFullname()));
        }
        return ComplaintResponse.from(complaint, LocalDateTime.now());
    }

    /** Resolving needs resolution notes. Starting work takes the complaint for the agent if nobody owns it. */
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ComplaintResponse updateStatus(Long id, String target, String note, String actorUsername) {
        Complaint complaint = complaints.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        ComplaintStatus next = parse(ComplaintStatus.class, target, "status");
        ComplaintStatus current = complaint.getStatus();
        if (current == ComplaintStatus.CLOSED) {
            throw new InvalidStatusException("This complaint is closed and cannot be reopened");
        }
        if (!current.next().contains(next)) {
            String allowed = current.next().stream().map(this::label).collect(Collectors.joining(" or "));
            throw new InvalidStatusException("A complaint that is " + label(current) + " can only move to " + allowed + ", not " + label(next));
        }
        String text = note == null || note.isBlank() ? null : note.trim();
        if (next == ComplaintStatus.RESOLVED && text == null) {
            throw new FieldValidationException("note", "Resolution notes are required when resolving a complaint");
        }
        User actor = findUser(actorUsername);
        if (next == ComplaintStatus.IN_PROGRESS && complaint.getAssignee() == null) {
            complaint.assignTo(actor);
        }
        complaint.moveTo(next, text);
        events.save(new ComplaintEvent(complaint, actor, EventType.STATUS_CHANGED, next, text));
        return ComplaintResponse.from(complaint, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(readOnly = true)
    public List<AgentResponse> agents() {
        Map<String, Long> active = complaints.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> c.getStatus().isActive() && c.getAssignee() != null)
                .collect(Collectors.groupingBy(c -> c.getAssignee().getUsername(), Collectors.counting()));
        return users.findByRoleOrderByFullnameAsc(Role.SUPPORT_AGENT).stream()
                .map(u -> new AgentResponse(u.getUsername(), u.getFullname(), active.getOrDefault(u.getUsername(), 0L))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(readOnly = true)
    public Statistics statistics(String agentUsername) {
        List<Complaint> all = complaints.findAllByOrderByCreatedAtDesc();
        LocalDateTime now = LocalDateTime.now();
        Map<ComplaintStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(Complaint::getStatus, Collectors.counting()));
        List<Complaint> active = all.stream().filter(c -> c.getStatus().isActive()).toList();
        long resolvedOrClosed = byStatus.getOrDefault(ComplaintStatus.RESOLVED, 0L) + byStatus.getOrDefault(ComplaintStatus.CLOSED, 0L);
        double hours = all.stream().filter(c -> c.getResolvedAt() != null)
                .mapToLong(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMinutes()).average().orElse(0) / 60.0;
        List<Integer> ratings = all.stream().map(Complaint::getRating).filter(r -> r != null).toList();
        double rating = ratings.stream().mapToInt(Integer::intValue).average().orElse(0);
        Map<Category, Long> byCategory = all.stream().collect(Collectors.groupingBy(Complaint::getCategory, Collectors.counting()));
        Map<Priority, Long> byPriority = active.stream().collect(Collectors.groupingBy(Complaint::getPriority, Collectors.counting()));
        LocalDate today = LocalDate.now();
        List<DayCount> week = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            week.add(new DayCount(day, all.stream().filter(c -> c.getCreatedAt().toLocalDate().equals(day)).count(),
                    all.stream().filter(c -> c.getResolvedAt() != null && c.getResolvedAt().toLocalDate().equals(day)).count()));
        }
        return new Statistics(all.size(), byStatus.getOrDefault(ComplaintStatus.OPEN, 0L), byStatus.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(ComplaintStatus.RESOLVED, 0L), byStatus.getOrDefault(ComplaintStatus.CLOSED, 0L),
                active.stream().filter(c -> c.getAssignee() == null).count(),
                active.stream().filter(c -> c.getAssignee() != null && c.getAssignee().getUsername().equals(agentUsername)).count(),
                active.stream().filter(c -> Duration.between(c.getCreatedAt(), now).toDays() >= 3).count(),
                all.isEmpty() ? 0 : (int) Math.round(100.0 * resolvedOrClosed / all.size()), Math.round(hours * 10) / 10.0,
                Math.round(rating * 10) / 10.0, ratings.size(),
                Arrays.stream(Category.values()).map(c -> new Count(c.name(), byCategory.getOrDefault(c, 0L))).collect(Collectors.toList()),
                Arrays.stream(Priority.values()).sorted(Comparator.reverseOrder()).map(p -> new Count(p.name(), byPriority.getOrDefault(p, 0L))).collect(Collectors.toList()),
                week);
    }

    // ---------- helpers ----------

    private String label(ComplaintStatus s) {
        return s.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean assigneeMatches(Complaint c, String assignee, String me) {
        String a = c.getAssignee() == null ? null : c.getAssignee().getUsername();
        return switch (assignee.toLowerCase(Locale.ROOT)) {
            case "unassigned" -> a == null;
            case "me" -> me.equals(a);
            default -> assignee.equals(a);
        };
    }

    private boolean matches(Complaint c, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return c.getSubject().toLowerCase(Locale.ROOT).contains(term) || c.getDescription().toLowerCase(Locale.ROOT).contains(term)
                || ComplaintResponse.numberOf(c.getId()).toLowerCase(Locale.ROOT).contains(term)
                || c.getCustomer().getFullname().toLowerCase(Locale.ROOT).contains(term);
    }

    private User findUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Complaint not found with id " + id);
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException(field, "Unknown " + field + " '" + value + "'");
        }
    }
}
