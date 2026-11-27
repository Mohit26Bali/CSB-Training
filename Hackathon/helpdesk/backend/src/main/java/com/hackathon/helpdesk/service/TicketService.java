package com.hackathon.helpdesk.service;

import java.time.Duration;
import java.time.LocalDateTime;
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

import com.hackathon.helpdesk.dto.AgentDashboard;
import com.hackathon.helpdesk.dto.AgentResponse;
import com.hackathon.helpdesk.dto.CommentResponse;
import com.hackathon.helpdesk.dto.Count;
import com.hackathon.helpdesk.dto.TicketDetail;
import com.hackathon.helpdesk.dto.TicketRequest;
import com.hackathon.helpdesk.dto.TicketResponse;
import com.hackathon.helpdesk.entity.Category;
import com.hackathon.helpdesk.entity.Priority;
import com.hackathon.helpdesk.entity.Role;
import com.hackathon.helpdesk.entity.Ticket;
import com.hackathon.helpdesk.entity.TicketComment;
import com.hackathon.helpdesk.entity.TicketStatus;
import com.hackathon.helpdesk.entity.User;
import com.hackathon.helpdesk.exception.BusinessRuleException;
import com.hackathon.helpdesk.exception.FieldValidationException;
import com.hackathon.helpdesk.exception.ForbiddenActionException;
import com.hackathon.helpdesk.exception.InvalidStatusException;
import com.hackathon.helpdesk.exception.ResourceNotFoundException;
import com.hackathon.helpdesk.repo.TicketCommentRepository;
import com.hackathon.helpdesk.repo.TicketRepository;
import com.hackathon.helpdesk.repo.UserRepository;

/**
 * Tickets, assignment, the status flow and comments. Every change to a ticket first takes its row lock (under
 * READ_COMMITTED), so two agents cannot resolve, close or reassign the same ticket at once.
 */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final UserRepository users;

    public TicketService(TicketRepository tickets, TicketCommentRepository comments, UserRepository users) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
    }

    // ---------- employee ----------

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public TicketResponse create(String username, TicketRequest request) {
        User requester = findUser(username);
        Category category = parse(Category.class, request.category(), "category");
        Priority priority = parse(Priority.class, request.priority(), "priority");
        Ticket saved = tickets.save(new Ticket(request.title().trim(), request.description().trim(), category, priority, requester));
        return TicketResponse.from(saved, 0, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<TicketResponse> mine(String username, TicketStatus status, String search) {
        User requester = findUser(username);
        Map<Long, Long> counts = commentCounts(false);
        LocalDateTime now = LocalDateTime.now();
        return tickets.findByRequesterOrderByCreatedAtDesc(requester).stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> matches(t, search))
                .map(t -> TicketResponse.from(t, counts.getOrDefault(t.getId(), 0L), now)).collect(Collectors.toList());
    }

    // ---------- both ----------

    /** A ticket with its conversation. Employees only see their own tickets and no internal notes. */
    @Transactional(readOnly = true)
    public TicketDetail detail(Long id, String username, boolean agent) {
        Ticket ticket = tickets.findWithPeopleById(id).orElseThrow(() -> notFound(id));
        if (!agent && !ticket.getRequester().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only view your own tickets");
        }
        List<CommentResponse> thread = comments.findByTicketOrderByCreatedAtAsc(ticket).stream()
                .filter(c -> agent || !c.isInternalNote()).map(CommentResponse::from).collect(Collectors.toList());
        return new TicketDetail(TicketResponse.from(ticket, thread.size(), LocalDateTime.now()), thread);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentResponse comment(Long id, String username, boolean agent, String body, boolean internal) {
        Ticket ticket = tickets.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        User author = findUser(username);
        if (!agent && !ticket.getRequester().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only comment on your own tickets");
        }
        if (internal && !agent) {
            throw new ForbiddenActionException("Only support agents can add internal notes");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidStatusException("This ticket is closed, so it cannot take new comments");
        }
        ticket.touch();
        return CommentResponse.from(comments.save(new TicketComment(ticket, author, body.trim(), internal)));
    }

    public Map<String, Object> meta() {
        return Map.of("categories", Arrays.stream(Category.values()).map(Enum::name).toList(),
                "priorities", Arrays.stream(Priority.values()).map(p -> Map.of("name", p.name(), "slaHours", p.getSlaHours())).toList());
    }

    // ---------- agent ----------

    /**
     * The queue. assignee: "me", "unassigned" or an agent's username. Active tickets come first, the one closest to (or past)
     * its SLA deadline on top; finished tickets follow, newest first.
     */
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(readOnly = true)
    public List<TicketResponse> queue(String agentUsername, TicketStatus status, Priority priority, String assignee, String search) {
        Map<Long, Long> counts = commentCounts(true);
        LocalDateTime now = LocalDateTime.now();
        return tickets.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> priority == null || t.getPriority() == priority)
                .filter(t -> assignee == null || assignee.isBlank() || assigneeMatches(t, assignee, agentUsername))
                .filter(t -> matches(t, search))
                .sorted(Comparator.comparing((Ticket t) -> !t.getStatus().isActive())
                        .thenComparing(t -> t.getStatus().isActive() ? t.getDueAt() : LocalDateTime.MAX)
                        .thenComparing(Ticket::getCreatedAt, Comparator.reverseOrder()))
                .map(t -> TicketResponse.from(t, counts.getOrDefault(t.getId(), 0L), now)).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TicketResponse assign(Long id, String assigneeUsername) {
        Ticket ticket = tickets.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!ticket.getStatus().isActive()) {
            throw new InvalidStatusException("A " + ticket.getStatus().name().toLowerCase(Locale.ROOT) + " ticket cannot be reassigned");
        }
        if (assigneeUsername == null || assigneeUsername.isBlank()) {
            if (ticket.getStatus() != TicketStatus.OPEN) {
                throw new BusinessRuleException("Work has started on this ticket, so it needs an owner. Reassign it instead.");
            }
            ticket.assignTo(null);
        } else {
            User agent = users.findByUsername(assigneeUsername.trim())
                    .filter(u -> u.getRole() == Role.SUPPORT_AGENT)
                    .orElseThrow(() -> new FieldValidationException("assignee", "'" + assigneeUsername + "' is not a support agent"));
            ticket.assignTo(agent);
        }
        return TicketResponse.from(ticket, 0, LocalDateTime.now());
    }

    /** Moves the ticket along OPEN, IN_PROGRESS, RESOLVED, CLOSED. Starting work takes the ticket if nobody owns it yet. */
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TicketResponse updateStatus(Long id, String target, String note, String actorUsername) {
        Ticket ticket = tickets.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        TicketStatus next = parse(TicketStatus.class, target, "status");
        TicketStatus current = ticket.getStatus();
        if (current == TicketStatus.CLOSED) {
            throw new InvalidStatusException("This ticket is closed and cannot be reopened");
        }
        if (!current.next().contains(next)) {
            String allowed = current.next().stream().map(s -> label(s)).collect(Collectors.joining(" or "));
            throw new InvalidStatusException("A ticket that is " + label(current) + " can only move to " + allowed + ", not " + label(next));
        }
        String text = note == null || note.isBlank() ? null : note.trim();
        if (next == TicketStatus.RESOLVED && text == null) {
            throw new FieldValidationException("note", "Describe how the problem was resolved");
        }
        if (next == TicketStatus.IN_PROGRESS && ticket.getAssignee() == null) {
            ticket.assignTo(findUser(actorUsername));
        }
        ticket.moveTo(next, text);
        return TicketResponse.from(ticket, 0, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(readOnly = true)
    public List<AgentResponse> agents() {
        Map<String, Long> active = tickets.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> t.getStatus().isActive() && t.getAssignee() != null)
                .collect(Collectors.groupingBy(t -> t.getAssignee().getUsername(), Collectors.counting()));
        return users.findByRoleOrderByFullnameAsc(Role.SUPPORT_AGENT).stream()
                .map(u -> new AgentResponse(u.getUsername(), u.getFullname(), active.getOrDefault(u.getUsername(), 0L))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    @Transactional(readOnly = true)
    public AgentDashboard dashboard(String agentUsername) {
        List<Ticket> all = tickets.findAllByOrderByCreatedAtDesc();
        LocalDateTime now = LocalDateTime.now();
        Map<TicketStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(Ticket::getStatus, Collectors.counting()));
        List<Ticket> active = all.stream().filter(t -> t.getStatus().isActive()).toList();
        long breached = active.stream().filter(t -> "BREACHED".equals(TicketResponse.slaState(t, now))).count();
        long atRisk = active.stream().filter(t -> "AT_RISK".equals(TicketResponse.slaState(t, now))).count();
        double avg = all.stream().filter(t -> t.getResolvedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes()).average().orElse(0) / 60.0;
        Map<Priority, Long> byPriority = active.stream().collect(Collectors.groupingBy(Ticket::getPriority, Collectors.counting()));
        List<Count> priorities = Arrays.stream(Priority.values()).sorted(Comparator.reverseOrder())
                .map(p -> new Count(p.name(), byPriority.getOrDefault(p, 0L))).collect(Collectors.toList());
        Map<Category, Long> byCategory = all.stream().collect(Collectors.groupingBy(Ticket::getCategory, Collectors.counting()));
        List<Count> categories = Arrays.stream(Category.values()).map(c -> new Count(c.name(), byCategory.getOrDefault(c, 0L))).collect(Collectors.toList());
        return new AgentDashboard(byStatus.getOrDefault(TicketStatus.OPEN, 0L), byStatus.getOrDefault(TicketStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(TicketStatus.RESOLVED, 0L), byStatus.getOrDefault(TicketStatus.CLOSED, 0L),
                active.stream().filter(t -> t.getAssignee() == null).count(),
                active.stream().filter(t -> t.getAssignee() != null && t.getAssignee().getUsername().equals(agentUsername)).count(),
                breached, atRisk, Math.round(avg * 10) / 10.0, priorities, categories);
    }

    // ---------- helpers ----------

    private String label(TicketStatus s) {
        return s.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean assigneeMatches(Ticket t, String assignee, String me) {
        String a = t.getAssignee() == null ? null : t.getAssignee().getUsername();
        return switch (assignee.toLowerCase(Locale.ROOT)) {
            case "unassigned" -> a == null;
            case "me" -> me.equals(a);
            default -> assignee.equals(a);
        };
    }

    private boolean matches(Ticket t, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return t.getTitle().toLowerCase(Locale.ROOT).contains(term) || t.getDescription().toLowerCase(Locale.ROOT).contains(term)
                || TicketResponse.numberOf(t.getId()).toLowerCase(Locale.ROOT).contains(term)
                || t.getRequester().getFullname().toLowerCase(Locale.ROOT).contains(term);
    }

    /** Comments per ticket; agents count internal notes too. */
    private Map<Long, Long> commentCounts(boolean agent) {
        return comments.idsAndKinds().stream().filter(r -> agent || !(Boolean) r[1])
                .collect(Collectors.groupingBy(r -> (Long) r[0], Collectors.counting()));
    }

    private User findUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Ticket not found with id " + id);
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException(field, "Unknown " + field + " '" + value + "'");
        }
    }
}
