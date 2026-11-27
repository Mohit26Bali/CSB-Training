package com.hackathon.complaints.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.hackathon.complaints.dto.ComplaintDetail;
import com.hackathon.complaints.dto.ComplaintRequest;
import com.hackathon.complaints.dto.ComplaintResponse;
import com.hackathon.complaints.dto.Statistics;
import com.hackathon.complaints.entity.Category;
import com.hackathon.complaints.entity.Complaint;
import com.hackathon.complaints.entity.ComplaintStatus;
import com.hackathon.complaints.entity.Priority;
import com.hackathon.complaints.entity.Role;
import com.hackathon.complaints.entity.User;
import com.hackathon.complaints.exception.BusinessRuleException;
import com.hackathon.complaints.exception.ConflictException;
import com.hackathon.complaints.exception.FieldValidationException;
import com.hackathon.complaints.exception.ForbiddenActionException;
import com.hackathon.complaints.exception.InvalidStatusException;
import com.hackathon.complaints.repo.ComplaintEventRepository;
import com.hackathon.complaints.repo.ComplaintRepository;
import com.hackathon.complaints.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class ComplaintServiceTest {

    @Autowired ComplaintService service;
    @Autowired ComplaintRepository complaints;
    @Autowired ComplaintEventRepository events;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        complaints.deleteAll();
        users.deleteAll();
        users.save(new User("agent1", "x", "Agent One", Role.SUPPORT_AGENT));
        users.save(new User("agent2", "x", "Agent Two", Role.SUPPORT_AGENT));
        users.save(new User("cust1", "x", "Customer One", Role.CUSTOMER));
        users.save(new User("cust2", "x", "Customer Two", Role.CUSTOMER));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    ComplaintResponse file(String customer, String subject, String priority) {
        loginAs(customer, Role.CUSTOMER);
        return service.file(customer, new ComplaintRequest(subject, "Something went wrong", "billing", priority));
    }

    ComplaintResponse status(Long id, String target, String note) {
        loginAs("agent1", Role.SUPPORT_AGENT);
        return service.updateStatus(id, target, note, "agent1");
    }

    ComplaintResponse resolve(Long id) {
        status(id, "in_progress", null);
        return status(id, "resolved", "Refund issued");
    }

    void backdate(Long id, int hoursAgo) {
        Complaint c = complaints.findById(id).orElseThrow();
        c.forceState(c.getStatus(), LocalDateTime.now().minusHours(hoursAgo), c.getResolvedAt(), c.getClosedAt(), c.getResolution());
        complaints.save(c);
    }

    // ---------- filing and viewing ----------

    @Test
    void customerFilesAComplaintWithAReferenceAndATimeline() {
        ComplaintResponse c = file("cust1", "  Charged twice  ", "high");
        assertEquals("OPEN", c.status());
        assertEquals("Charged twice", c.subject());
        assertTrue(c.number().startsWith("CMP-"));
        assertEquals(List.of("IN_PROGRESS"), c.allowedNext());
        ComplaintDetail detail = service.detail(c.id(), "cust1", false);
        assertEquals(1, detail.timeline().size());
        assertEquals("FILED", detail.timeline().get(0).type());
    }

    @Test
    void unknownCategoryOrPriorityIsAFieldError() {
        loginAs("cust1", Role.CUSTOMER);
        FieldValidationException cat = assertThrows(FieldValidationException.class,
                () -> service.file("cust1", new ComplaintRequest("s", "d", "weather", "low")));
        assertEquals("category", cat.getField());
        FieldValidationException pri = assertThrows(FieldValidationException.class,
                () -> service.file("cust1", new ComplaintRequest("s", "d", "billing", "meh")));
        assertEquals("priority", pri.getField());
    }

    @Test
    void customersSeeOnlyTheirOwnComplaintsAndCanFilter() {
        file("cust1", "Late parcel", "low");
        ComplaintResponse second = file("cust1", "Cracked jar", "medium");
        file("cust2", "Someone else's", "low");
        status(second.id(), "in_progress", null);
        loginAs("cust1", Role.CUSTOMER);
        assertEquals(2, service.mine("cust1", null, null).size());
        assertEquals(1, service.mine("cust1", ComplaintStatus.IN_PROGRESS, null).size());
        assertEquals(1, service.mine("cust1", null, "cracked").size());
        assertEquals(1, service.mine("cust1", null, second.number()).size());
        loginAs("cust2", Role.CUSTOMER);
        assertEquals(1, service.mine("cust2", null, null).size());
    }

    @Test
    void aCustomerCannotOpenAnotherCustomersComplaintButAnAgentCan() {
        ComplaintResponse c = file("cust1", "Private", "low");
        loginAs("cust2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> service.detail(c.id(), "cust2", false));
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertEquals("Private", service.detail(c.id(), "agent1", true).complaint().subject());
    }

    // ---------- resolving ----------

    @Test
    void resolvingNeedsResolutionNotesAndTheCustomerSeesThem() {
        ComplaintResponse c = file("cust1", "Refund missing", "urgent");
        status(c.id(), "in_progress", "On it");
        FieldValidationException noNote = assertThrows(FieldValidationException.class, () -> status(c.id(), "resolved", "   "));
        assertEquals("note", noNote.getField());
        assertThrows(FieldValidationException.class, () -> status(c.id(), "resolved", null));
        ComplaintResponse resolved = status(c.id(), "resolved", "Refund processed to the original card");
        assertEquals("Refund processed to the original card", resolved.resolution());
        assertTrue(resolved.canRate());
        loginAs("cust1", Role.CUSTOMER);
        assertEquals("Refund processed to the original card", service.detail(c.id(), "cust1", false).complaint().resolution());
    }

    @Test
    void theStatusFlowMovesOneStepAtATimeAndClosedIsFinal() {
        ComplaintResponse c = file("cust1", "Wrong item", "medium");
        InvalidStatusException skip = assertThrows(InvalidStatusException.class, () -> status(c.id(), "resolved", "done"));
        assertTrue(skip.getMessage().contains("in progress"));
        assertEquals("agent1", status(c.id(), "IN_PROGRESS", null).assigneeUsername());
        status(c.id(), "resolved", "Replacement sent");
        ComplaintResponse again = status(c.id(), "in_progress", "Replacement also wrong");
        assertNull(again.resolvedAt());
        status(c.id(), "resolved", "Correct item sent");
        ComplaintResponse closed = status(c.id(), "closed", null);
        assertEquals(List.of(), closed.allowedNext());
        InvalidStatusException ex = assertThrows(InvalidStatusException.class, () -> status(c.id(), "in_progress", null));
        assertTrue(ex.getMessage().contains("cannot be reopened"));
        assertThrows(FieldValidationException.class, () -> status(c.id(), "done", null));
    }

    @Test
    void theTimelineRecordsEveryStep() {
        ComplaintResponse c = file("cust1", "Timeline", "low");
        loginAs("agent1", Role.SUPPORT_AGENT);
        service.assign(c.id(), "agent2", "agent1");
        status(c.id(), "in_progress", "Checking");
        status(c.id(), "resolved", "Fixed");
        ComplaintDetail detail = service.detail(c.id(), "agent1", true);
        assertEquals(List.of("FILED", "ASSIGNED", "STATUS_CHANGED", "STATUS_CHANGED"), detail.timeline().stream().map(e -> e.type()).toList());
        assertEquals("Assigned to Agent Two", detail.timeline().get(1).note());
        assertEquals("RESOLVED", detail.timeline().get(3).status());
    }

    // ---------- assignment ----------

    @Test
    void agentsAssignReassignAndUnassignWhileOpen() {
        ComplaintResponse c = file("cust1", "Assign me", "low");
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertEquals("agent2", service.assign(c.id(), "agent2", "agent1").assigneeUsername());
        assertNull(service.assign(c.id(), "", "agent1").assigneeUsername());
        FieldValidationException notAgent = assertThrows(FieldValidationException.class, () -> service.assign(c.id(), "cust2", "agent1"));
        assertEquals("assignee", notAgent.getField());
    }

    @Test
    void workInProgressKeepsItsOwnerAndFinishedComplaintsCannotBeReassigned() {
        ComplaintResponse c = file("cust1", "Owner", "low");
        status(c.id(), "in_progress", null);
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertThrows(BusinessRuleException.class, () -> service.assign(c.id(), null, "agent1"));
        status(c.id(), "resolved", "Done");
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertThrows(InvalidStatusException.class, () -> service.assign(c.id(), "agent2", "agent1"));
    }

    // ---------- feedback ----------

    @Test
    void customersRateAResolvedComplaintOnce() {
        ComplaintResponse c = file("cust1", "Rate me", "low");
        loginAs("cust1", Role.CUSTOMER);
        assertThrows(InvalidStatusException.class, () -> service.feedback(c.id(), "cust1", 5, "early")); // not resolved yet
        resolve(c.id());
        loginAs("cust2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> service.feedback(c.id(), "cust2", 5, null));
        loginAs("cust1", Role.CUSTOMER);
        ComplaintResponse rated = service.feedback(c.id(), "cust1", 4, "  Good  ");
        assertEquals(4, rated.rating());
        assertEquals("Good", rated.feedback());
        assertTrue(!rated.canRate());
        assertThrows(ConflictException.class, () -> service.feedback(c.id(), "cust1", 5, null));
        assertEquals("FEEDBACK", service.detail(c.id(), "cust1", false).timeline().get(service.detail(c.id(), "cust1", false).timeline().size() - 1).type());
    }

    // ---------- queue and statistics ----------

    @Test
    void queueShowsTheMostUrgentAndLongestWaitingFirstAndFinishedLast() {
        ComplaintResponse low = file("cust1", "Low old", "low");
        ComplaintResponse urgent = file("cust1", "Urgent new", "urgent");
        ComplaintResponse highOld = file("cust2", "High old", "high");
        ComplaintResponse highNew = file("cust2", "High new", "high");
        ComplaintResponse done = file("cust2", "Done", "urgent");
        backdate(low.id(), 100);
        backdate(highOld.id(), 50);
        resolve(done.id());
        loginAs("agent2", Role.SUPPORT_AGENT);
        List<Long> order = service.queue("agent2", null, null, null, null, null).stream().map(ComplaintResponse::id).toList();
        assertEquals(List.of(urgent.id(), highOld.id(), highNew.id(), low.id(), done.id()), order);
    }

    @Test
    void queueFiltersByStatusPriorityCategoryAssigneeAndText() {
        ComplaintResponse a = file("cust1", "Charged twice", "high");
        ComplaintResponse b = file("cust2", "Late parcel", "low");
        status(a.id(), "in_progress", null);
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertEquals(1, service.queue("agent1", ComplaintStatus.OPEN, null, null, null, null).size());
        assertEquals(1, service.queue("agent1", null, Priority.HIGH, null, null, null).size());
        assertEquals(2, service.queue("agent1", null, null, Category.BILLING, null, null).size());
        assertEquals(0, service.queue("agent1", null, null, Category.DELIVERY, null, null).size());
        assertEquals(List.of(a.id()), service.queue("agent1", null, null, null, "me", null).stream().map(ComplaintResponse::id).toList());
        assertEquals(List.of(b.id()), service.queue("agent1", null, null, null, "unassigned", null).stream().map(ComplaintResponse::id).toList());
        assertEquals(1, service.queue("agent1", null, null, null, null, "customer two").size());
        assertEquals(1, service.queue("agent1", null, null, null, null, b.number()).size());
    }

    @Test
    void statisticsCoverCountsRatingsAndTheWeek() {
        ComplaintResponse a = file("cust1", "A", "urgent");
        file("cust1", "B", "low");
        ComplaintResponse c = file("cust2", "C", "high");
        backdate(a.id(), 100);
        resolve(c.id());
        loginAs("cust2", Role.CUSTOMER);
        service.feedback(c.id(), "cust2", 4, null);
        loginAs("agent1", Role.SUPPORT_AGENT);
        Statistics s = service.statistics("agent1");
        assertEquals(3, s.total());
        assertEquals(2, s.open());
        assertEquals(1, s.resolved());
        assertEquals(2, s.unassigned());
        assertEquals(1, s.waitingOverThreeDays());
        assertEquals(33, s.resolutionRatePercent());
        assertEquals(4.0, s.averageRating());
        assertEquals(1, s.ratings());
        assertEquals(6, s.byCategory().size());
        assertEquals("URGENT", s.activeByPriority().get(0).name());
        assertEquals(7, s.lastSevenDays().size());
        assertEquals(2, service.agents().size());
    }

    @Test
    void rolesAreEnforced() {
        loginAs("cust1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> service.queue("cust1", null, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(1L, "resolved", "x", "cust1"));
        assertThrows(AccessDeniedException.class, () -> service.statistics("cust1"));
        loginAs("agent1", Role.SUPPORT_AGENT);
        assertThrows(AccessDeniedException.class, () -> service.file("agent1", new ComplaintRequest("s", "d", "billing", "low")));
        assertThrows(AccessDeniedException.class, () -> service.feedback(1L, "agent1", 5, null));
    }

    // ---------- concurrency ----------

    @Test
    void twoAgentsResolvingTheSameComplaintResolveItOnce() throws Exception {
        ComplaintResponse c = file("cust1", "Contended", "high");
        status(c.id(), "in_progress", null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 2).<Callable<Boolean>>mapToObj(i -> () -> {
                String agent = "agent" + (i + 1);
                loginAs(agent, Role.SUPPORT_AGENT);
                try {
                    service.updateStatus(c.id(), "resolved", "Fixed by " + agent, agent);
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
    void doubleClickingFeedbackRatesOnce() throws Exception {
        ComplaintResponse c = file("cust1", "Rate twice", "low");
        resolve(c.id());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("cust1", Role.CUSTOMER);
                try {
                    service.feedback(c.id(), "cust1", 3 + i % 3, "try " + i);
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, events.findAll().stream().filter(e -> e.getType().name().equals("FEEDBACK")).count());
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
