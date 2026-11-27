package com.hackathon.helpdesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.hackathon.helpdesk.dto.AgentDashboard;
import com.hackathon.helpdesk.dto.TicketDetail;
import com.hackathon.helpdesk.dto.TicketRequest;
import com.hackathon.helpdesk.dto.TicketResponse;
import com.hackathon.helpdesk.entity.Category;
import com.hackathon.helpdesk.entity.Priority;
import com.hackathon.helpdesk.entity.Role;
import com.hackathon.helpdesk.entity.Ticket;
import com.hackathon.helpdesk.entity.TicketStatus;
import com.hackathon.helpdesk.entity.User;
import com.hackathon.helpdesk.exception.BusinessRuleException;
import com.hackathon.helpdesk.exception.FieldValidationException;
import com.hackathon.helpdesk.exception.ForbiddenActionException;
import com.hackathon.helpdesk.exception.InvalidStatusException;
import com.hackathon.helpdesk.repo.TicketCommentRepository;
import com.hackathon.helpdesk.repo.TicketRepository;
import com.hackathon.helpdesk.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class TicketServiceTest {

    @Autowired TicketService service;
    @Autowired TicketRepository tickets;
    @Autowired TicketCommentRepository comments;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        comments.deleteAll();
        tickets.deleteAll();
        users.deleteAll();
        users.save(new User("agent1", "x", "Agent One", Role.SUPPORT_AGENT));
        users.save(new User("agent2", "x", "Agent Two", Role.SUPPORT_AGENT));
        users.save(new User("emp1", "x", "Employee One", Role.EMPLOYEE));
        users.save(new User("emp2", "x", "Employee Two", Role.EMPLOYEE));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    TicketResponse create(String employee, String title, String priority) {
        loginAs(employee, Role.EMPLOYEE);
        return service.create(employee, new TicketRequest(title, "Something is broken", "hardware", priority));
    }

    TicketResponse status(Long id, String target, String note) {
        loginAs("agent1", Role.SUPPORT_AGENT);
        return service.updateStatus(id, target, note, "agent1");
    }

    /** Moves a ticket's start into the past by editing it directly. */
    void backdate(Long id, int hoursAgo) {
        Ticket t = tickets.findById(id).orElseThrow();
        t.startedAt(LocalDateTime.now().minusHours(hoursAgo));
        tickets.save(t);
    }

    // ---------- creating and viewing ----------

    @Test
    void employeeCreatesATicketWithAReferenceAndASlaDeadline() {
        TicketResponse t = create("emp1", "  Laptop is dead  ", "high");
        assertEquals("OPEN", t.status());
        assertEquals("Laptop is dead", t.title());
        assertEquals("HIGH", t.priority());
        assertTrue(t.number().startsWith("HD-"));
        assertEquals(t.createdAt().plusHours(8), t.dueAt());
        assertEquals("ON_TRACK", t.slaState());
        assertEquals(List.of("IN_PROGRESS"), t.allowedNext());
    }

    @Test
    void unknownCategoryOrPriorityIsAFieldError() {
        loginAs("emp1", Role.EMPLOYEE);
        FieldValidationException cat = assertThrows(FieldValidationException.class,
                () -> service.create("emp1", new TicketRequest("t", "d", "plumbing", "low")));
        assertEquals("category", cat.getField());
        FieldValidationException pri = assertThrows(FieldValidationException.class,
                () -> service.create("emp1", new TicketRequest("t", "d", "hardware", "urgent")));
        assertEquals("priority", pri.getField());
    }

    @Test
    void employeesSeeOnlyTheirOwnTicketsAndCanFilter() {
        create("emp1", "Mouse broken", "low");
        TicketResponse second = create("emp1", "VPN down", "high");
        create("emp2", "Other person's ticket", "low");
        status(second.id(), "in_progress", null);
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals(2, service.mine("emp1", null, null).size());
        assertEquals(1, service.mine("emp1", TicketStatus.IN_PROGRESS, null).size());
        assertEquals(1, service.mine("emp1", null, "vpn").size());
        assertEquals(1, service.mine("emp1", null, second.number()).size());
        loginAs("emp2", Role.EMPLOYEE);
        assertEquals(1, service.mine("emp2", null, null).size());
    }

    @Test
    void anEmployeeCannotOpenSomeoneElsesTicketButAnAgentCan() {
        TicketResponse t = create("emp1", "Private issue", "low");
        loginAs("emp2", Role.EMPLOYEE);
        assertThrows(ForbiddenActionException.class, () -> service.detail(t.id(), "emp2", false));
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertEquals("Private issue", service.detail(t.id(), "agent1", true).ticket().title());
    }

    // ---------- comments ----------

    @Test
    void internalNotesAreHiddenFromTheRequester() {
        TicketResponse t = create("emp1", "Slow wifi", "medium");
        loginAs("emp1", Role.EMPLOYEE);
        service.comment(t.id(), "emp1", false, "It is slow in the east wing", false);
        loginAs("agent1", Role.SUPPORT_AGENT);
        service.comment(t.id(), "agent1", true, "Probably AP-4E again", true);
        service.comment(t.id(), "agent1", true, "Looking into it", false);
        TicketDetail forAgent = service.detail(t.id(), "agent1", true);
        assertEquals(3, forAgent.comments().size());
        loginAs("emp1", Role.EMPLOYEE);
        TicketDetail forEmployee = service.detail(t.id(), "emp1", false);
        assertEquals(2, forEmployee.comments().size());
        assertTrue(forEmployee.comments().stream().noneMatch(c -> c.internal()));
        assertEquals(2, service.mine("emp1", null, null).get(0).commentCount());
    }

    @Test
    void commentingRulesAreEnforced() {
        TicketResponse t = create("emp1", "Printer", "low");
        loginAs("emp2", Role.EMPLOYEE);
        assertThrows(ForbiddenActionException.class, () -> service.comment(t.id(), "emp2", false, "me too", false));
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(ForbiddenActionException.class, () -> service.comment(t.id(), "emp1", false, "secret", true));
        status(t.id(), "in_progress", null);
        status(t.id(), "resolved", "Fixed");
        status(t.id(), "closed", null);
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(InvalidStatusException.class, () -> service.comment(t.id(), "emp1", false, "thanks", false));
    }

    // ---------- status flow ----------

    @Test
    void startingWorkTakesTheTicketForTheAgent() {
        TicketResponse t = create("emp1", "Docker", "low");
        assertNull(t.assigneeUsername());
        TicketResponse started = status(t.id(), "IN_PROGRESS", null);
        assertEquals("IN_PROGRESS", started.status());
        assertEquals("agent1", started.assigneeUsername());
        assertEquals(List.of("RESOLVED"), started.allowedNext());
    }

    @Test
    void ticketsMoveOneStepAtATimeAndResolvingNeedsANote() {
        TicketResponse t = create("emp1", "Excel macro", "high");
        InvalidStatusException skip = assertThrows(InvalidStatusException.class, () -> status(t.id(), "resolved", "done"));
        assertTrue(skip.getMessage().contains("in progress"));
        status(t.id(), "in_progress", null);
        FieldValidationException noNote = assertThrows(FieldValidationException.class, () -> status(t.id(), "resolved", "  "));
        assertEquals("note", noNote.getField());
        TicketResponse resolved = status(t.id(), "resolved", "Signed the workbook");
        assertEquals("Signed the workbook", resolved.resolution());
        assertNotNull(resolved.resolvedAt());
        assertEquals(List.of("CLOSED", "IN_PROGRESS"), resolved.allowedNext());
        assertThrows(FieldValidationException.class, () -> status(t.id(), "done", null));
    }

    @Test
    void aResolvedTicketCanGoBackToInProgressButAClosedOneCannotBeReopened() {
        TicketResponse t = create("emp1", "Jira slow", "high");
        status(t.id(), "in_progress", null);
        status(t.id(), "resolved", "Restarted");
        TicketResponse reopened = status(t.id(), "in_progress", null);
        assertEquals("IN_PROGRESS", reopened.status());
        assertNull(reopened.resolvedAt());
        status(t.id(), "resolved", "Raised the pool size");
        TicketResponse closed = status(t.id(), "closed", null);
        assertEquals("CLOSED", closed.status());
        assertEquals(List.of(), closed.allowedNext());
        InvalidStatusException ex = assertThrows(InvalidStatusException.class, () -> status(t.id(), "in_progress", null));
        assertTrue(ex.getMessage().contains("cannot be reopened"));
        assertThrows(InvalidStatusException.class, () -> status(t.id(), "open", null));
    }

    // ---------- assignment ----------

    @Test
    void agentsAssignAndReassignActiveTickets() {
        TicketResponse t = create("emp1", "Monitor", "low");
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertEquals("agent2", service.assign(t.id(), "agent2").assigneeUsername());
        assertEquals("OPEN", service.assign(t.id(), "agent2").status()); // assigning does not start work
        assertNull(service.assign(t.id(), "").assigneeUsername()); // back to the queue while still open
        FieldValidationException notAgent = assertThrows(FieldValidationException.class, () -> service.assign(t.id(), "emp2"));
        assertEquals("assignee", notAgent.getField());
        assertThrows(FieldValidationException.class, () -> service.assign(t.id(), "nobody"));
    }

    @Test
    void workInProgressMustKeepAnOwnerAndFinishedTicketsCannotBeReassigned() {
        TicketResponse t = create("emp1", "Keyboard", "low");
        status(t.id(), "in_progress", null);
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertThrows(BusinessRuleException.class, () -> service.assign(t.id(), null));
        assertEquals("agent2", service.assign(t.id(), "agent2").assigneeUsername());
        status(t.id(), "resolved", "Replaced");
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertThrows(InvalidStatusException.class, () -> service.assign(t.id(), "agent1"));
    }

    // ---------- the queue, SLA and dashboard ----------

    @Test
    void queuePutsTheMostUrgentTicketFirstAndFinishedOnesLast() {
        TicketResponse low = create("emp1", "Low one", "low");
        TicketResponse breached = create("emp1", "Late one", "critical");
        TicketResponse done = create("emp2", "Finished", "medium");
        backdate(breached.id(), 10); // critical SLA is 4 hours
        status(done.id(), "in_progress", null);
        status(done.id(), "resolved", "Done");
        loginAs("agent2", Role.SUPPORT_AGENT);
        List<TicketResponse> queue = service.queue("agent2", null, null, null, null);
        assertEquals(List.of(breached.id(), low.id(), done.id()), queue.stream().map(TicketResponse::id).toList());
        assertEquals("BREACHED", queue.get(0).slaState());
        assertTrue(queue.get(0).hoursLeft() < 0);
        assertEquals("MET", queue.get(2).slaState());
    }

    @Test
    void slaStatesCoverOnTrackAtRiskBreachedMetAndMissed() {
        TicketResponse onTrack = create("emp1", "A", "medium");
        TicketResponse atRisk = create("emp1", "B", "medium");
        TicketResponse late = create("emp1", "C", "medium");
        backdate(atRisk.id(), 20); // 24 hour SLA, 4 left: under a quarter
        backdate(late.id(), 30);
        loginAs("agent1", Role.SUPPORT_AGENT);
        List<TicketResponse> queue = service.queue("agent1", null, null, null, null);
        assertEquals("ON_TRACK", queue.stream().filter(t -> t.id().equals(onTrack.id())).findFirst().orElseThrow().slaState());
        assertEquals("AT_RISK", queue.stream().filter(t -> t.id().equals(atRisk.id())).findFirst().orElseThrow().slaState());
        assertEquals("BREACHED", queue.stream().filter(t -> t.id().equals(late.id())).findFirst().orElseThrow().slaState());
        status(late.id(), "in_progress", null);
        TicketResponse missed = status(late.id(), "resolved", "Fixed late");
        assertEquals("MISSED", missed.slaState());
    }

    @Test
    void queueFiltersByStatusPriorityAssigneeAndText() {
        TicketResponse a = create("emp1", "VPN down", "high");
        TicketResponse b = create("emp2", "Printer jam", "low");
        status(a.id(), "in_progress", null); // taken by agent1
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertEquals(1, service.queue("agent1", TicketStatus.OPEN, null, null, null).size());
        assertEquals(1, service.queue("agent1", null, Priority.HIGH, null, null).size());
        assertEquals(List.of(a.id()), service.queue("agent1", null, null, "me", null).stream().map(TicketResponse::id).toList());
        assertEquals(List.of(b.id()), service.queue("agent1", null, null, "unassigned", null).stream().map(TicketResponse::id).toList());
        assertEquals(1, service.queue("agent1", null, null, "agent1", null).size());
        assertEquals(1, service.queue("agent1", null, null, null, "employee two").size());
        assertEquals(1, service.queue("agent1", null, null, null, b.number()).size());
        assertEquals(0, service.queue("agent1", null, null, "agent2", null).size());
    }

    @Test
    void dashboardCountsTicketsAndSlaProblems() {
        TicketResponse a = create("emp1", "A", "critical");
        create("emp1", "B", "low");
        TicketResponse c = create("emp2", "C", "medium");
        backdate(a.id(), 10);
        status(c.id(), "in_progress", null);
        status(c.id(), "resolved", "Done");
        loginAs("agent1", Role.SUPPORT_AGENT);
        AgentDashboard d = service.dashboard("agent1");
        assertEquals(2, d.open());
        assertEquals(1, d.resolved());
        assertEquals(2, d.unassigned());
        assertEquals(1, d.breached());
        assertEquals(0, d.mine());
        assertFalse(d.averageResolutionHours() < 0);
        assertEquals(6, d.byCategory().size());
        assertEquals("CRITICAL", d.activeByPriority().get(0).name());
        assertEquals(1, d.activeByPriority().get(0).count());
        assertEquals(2, service.agents().size());
    }

    @Test
    void rolesAreEnforced() {
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> service.queue("emp1", null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(1L, "in_progress", null, "emp1"));
        assertThrows(AccessDeniedException.class, () -> service.assign(1L, "agent1"));
        assertThrows(AccessDeniedException.class, () -> service.dashboard("emp1"));
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertThrows(AccessDeniedException.class, () -> service.create("agent1", new TicketRequest("t", "d", "hardware", "low")));
        assertThrows(AccessDeniedException.class, () -> service.mine("agent1", null, null));
    }

    // ---------- concurrency ----------

    @Test
    void fiveAgentsStartingTheSameTicketStartItOnce() throws Exception {
        TicketResponse t = create("emp1", "Contended", "high");
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs(i % 2 == 0 ? "agent1" : "agent2", Role.SUPPORT_AGENT);
                try {
                    service.updateStatus(t.id(), "in_progress", null, i % 2 == 0 ? "agent1" : "agent2");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoAgentsResolvingTheSameTicketResolveItOnce() throws Exception {
        TicketResponse t = create("emp1", "Double resolve", "high");
        status(t.id(), "in_progress", null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 2).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("agent" + (i + 1), Role.SUPPORT_AGENT);
                try {
                    service.updateStatus(t.id(), "resolved", "Fixed by agent" + (i + 1), "agent" + (i + 1));
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
        } finally {
            pool.shutdownNow();
        }
    }

    private long winners(List<Future<Boolean>> results) throws Exception {
        long count = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                count++;
            }
        }
        return count;
    }
}
