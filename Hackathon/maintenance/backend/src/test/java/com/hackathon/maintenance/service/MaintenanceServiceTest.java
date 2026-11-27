package com.hackathon.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.hackathon.maintenance.dto.Dashboard;
import com.hackathon.maintenance.dto.NewRequest;
import com.hackathon.maintenance.dto.RequestResponse;
import com.hackathon.maintenance.dto.TechnicianBody;
import com.hackathon.maintenance.dto.TechnicianResponse;
import com.hackathon.maintenance.entity.Category;
import com.hackathon.maintenance.entity.MaintenanceRequest;
import com.hackathon.maintenance.entity.Priority;
import com.hackathon.maintenance.entity.RequestStatus;
import com.hackathon.maintenance.entity.Role;
import com.hackathon.maintenance.entity.Technician;
import com.hackathon.maintenance.entity.User;
import com.hackathon.maintenance.exception.BusinessRuleException;
import com.hackathon.maintenance.exception.FieldValidationException;
import com.hackathon.maintenance.exception.ForbiddenActionException;
import com.hackathon.maintenance.exception.InvalidStatusException;
import com.hackathon.maintenance.repo.MaintenanceRequestRepository;
import com.hackathon.maintenance.repo.TechnicianRepository;
import com.hackathon.maintenance.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class MaintenanceServiceTest {

    @Autowired MaintenanceService service;
    @Autowired MaintenanceRequestRepository requests;
    @Autowired TechnicianRepository technicians;
    @Autowired UserRepository users;

    Technician plumber;
    Technician electrician;
    Technician retired;

    @BeforeEach
    void setUp() {
        requests.deleteAll();
        technicians.deleteAll();
        users.deleteAll();
        users.save(new User("mgr", "x", "Manager", Role.FACILITY_MANAGER));
        users.save(new User("res1", "x", "Resident One", Role.RESIDENT));
        users.save(new User("res2", "x", "Resident Two", Role.RESIDENT));
        plumber = technicians.save(new Technician("Ramesh", Category.PLUMBING, "+91 98450 11201"));
        electrician = technicians.save(new Technician("Suresh", Category.ELECTRICAL, null));
        retired = new Technician("Dinesh", Category.PLUMBING, null);
        retired.setActive(false);
        retired = technicians.save(retired);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    RequestResponse raise(String resident, String category, String location, String priority) {
        loginAs(resident, Role.RESIDENT);
        return service.raise(resident, new NewRequest(category, "Something is wrong here", location, priority));
    }

    RequestResponse assign(Long id, Technician technician) {
        loginAs("mgr", Role.FACILITY_MANAGER);
        return service.assign(id, technician.getId());
    }

    RequestResponse status(Long id, String target, String notes) {
        loginAs("mgr", Role.FACILITY_MANAGER);
        return service.updateStatus(id, target, notes);
    }

    RequestResponse finish(Long id) {
        status(id, "in_progress", null);
        return status(id, "resolved", "Fixed");
    }

    void backdate(Long id, int hoursAgo) {
        MaintenanceRequest r = requests.findById(id).orElseThrow();
        r.forceState(r.getStatus(), r.getTechnician(), LocalDateTime.now().minusHours(hoursAgo), r.getAssignedAt(), r.getStartedAt(),
                r.getResolvedAt(), r.getClosedAt(), r.getResolutionNotes());
        requests.save(r);
    }

    // ---------- residents ----------

    @Test
    void residentRaisesARequestWithAReferenceAndStartsOpen() {
        RequestResponse r = raise("res1", "plumbing", "  Block B, Flat 402  ", "high");
        assertEquals("OPEN", r.status());
        assertEquals("PLUMBING", r.category());
        assertEquals("Block B, Flat 402", r.location());
        assertTrue(r.number().startsWith("MR-"));
        assertEquals("FRESH", r.aging());
        assertNull(r.technicianName());
        assertEquals(List.of("ASSIGNED"), r.allowedNext());
    }

    @Test
    void unknownCategoryOrPriorityIsAFieldError() {
        loginAs("res1", Role.RESIDENT);
        assertEquals("category", assertThrows(FieldValidationException.class,
                () -> service.raise("res1", new NewRequest("magic", "d", "l", "low"))).getField());
        assertEquals("priority", assertThrows(FieldValidationException.class,
                () -> service.raise("res1", new NewRequest("plumbing", "d", "l", "whenever"))).getField());
    }

    @Test
    void residentsSeeOnlyTheirOwnRequestsAndCanFilter() {
        raise("res1", "plumbing", "A-101", "low");
        RequestResponse second = raise("res1", "lift", "Lobby", "emergency");
        raise("res2", "garden", "Park", "low");
        assign(second.id(), electrician);
        loginAs("res1", Role.RESIDENT);
        assertEquals(2, service.mine("res1", null, null).size());
        assertEquals(1, service.mine("res1", RequestStatus.ASSIGNED, null).size());
        assertEquals(1, service.mine("res1", null, "lobby").size());
        assertEquals(1, service.mine("res1", null, second.number()).size());
        loginAs("res2", Role.RESIDENT);
        assertEquals(1, service.mine("res2", null, null).size());
    }

    @Test
    void aResidentCannotOpenAnotherResidentsRequestButAManagerCan() {
        RequestResponse r = raise("res1", "plumbing", "A-101", "low");
        loginAs("res2", Role.RESIDENT);
        assertThrows(ForbiddenActionException.class, () -> service.detail(r.id(), "res2", false));
        loginAs("mgr", Role.FACILITY_MANAGER);
        assertEquals("A-101", service.detail(r.id(), "mgr", true).location());
    }

    // ---------- assignment and status ----------

    @Test
    void assigningATechnicianMovesAnOpenRequestToAssigned() {
        RequestResponse r = raise("res1", "plumbing", "A-101", "high");
        RequestResponse assigned = assign(r.id(), plumber);
        assertEquals("ASSIGNED", assigned.status());
        assertEquals("Ramesh", assigned.technicianName());
        assertEquals(List.of("IN_PROGRESS"), assigned.allowedNext());
    }

    @Test
    void reassignmentKeepsTheStatusAndInactiveOrUnknownTechniciansAreRefused() {
        RequestResponse r = raise("res1", "plumbing", "A-101", "high");
        assign(r.id(), plumber);
        status(r.id(), "in_progress", null);
        RequestResponse moved = assign(r.id(), electrician);
        assertEquals("IN_PROGRESS", moved.status());
        assertEquals("Suresh", moved.technicianName());
        assertThrows(BusinessRuleException.class, () -> assign(r.id(), retired));
        loginAs("mgr", Role.FACILITY_MANAGER);
        FieldValidationException unknown = assertThrows(FieldValidationException.class, () -> service.assign(r.id(), 9999L));
        assertEquals("technicianId", unknown.getField());
    }

    @Test
    void theStatusFlowMovesOneStepAtATimeAndAssignedOnlyComesFromAssigning() {
        RequestResponse r = raise("res1", "lift", "Lobby", "emergency");
        BusinessRuleException toAssigned = assertThrows(BusinessRuleException.class, () -> status(r.id(), "assigned", null));
        assertTrue(toAssigned.getMessage().contains("Assign a technician"));
        InvalidStatusException skip = assertThrows(InvalidStatusException.class, () -> status(r.id(), "in_progress", null));
        assertTrue(skip.getMessage().contains("assigned"));
        assign(r.id(), electrician);
        assertThrows(InvalidStatusException.class, () -> status(r.id(), "resolved", "x")); // must start first
        assertEquals("IN_PROGRESS", status(r.id(), "IN_PROGRESS", null).status());
        assertThrows(FieldValidationException.class, () -> status(r.id(), "done", null));
    }

    @Test
    void resolvingNeedsNotesWhichTheResidentSees() {
        RequestResponse r = raise("res1", "plumbing", "A-101", "high");
        assign(r.id(), plumber);
        status(r.id(), "in_progress", null);
        FieldValidationException blank = assertThrows(FieldValidationException.class, () -> status(r.id(), "resolved", "   "));
        assertEquals("notes", blank.getField());
        assertThrows(FieldValidationException.class, () -> status(r.id(), "resolved", null));
        RequestResponse resolved = status(r.id(), "resolved", "Replaced the joint");
        assertEquals("Replaced the joint", resolved.resolutionNotes());
        assertNull(resolved.aging());
        loginAs("res1", Role.RESIDENT);
        assertEquals("Replaced the joint", service.mine("res1", null, null).get(0).resolutionNotes());
    }

    @Test
    void aResolvedRequestCanBeReworkedButAClosedOneIsFinal() {
        RequestResponse r = raise("res1", "plumbing", "A-101", "low");
        assign(r.id(), plumber);
        finish(r.id());
        RequestResponse reworked = status(r.id(), "in_progress", "Leak is back");
        assertEquals("IN_PROGRESS", reworked.status());
        assertNull(reworked.resolvedAt());
        status(r.id(), "resolved", "Replaced the washer too");
        RequestResponse closed = status(r.id(), "closed", null);
        assertEquals(List.of(), closed.allowedNext());
        InvalidStatusException ex = assertThrows(InvalidStatusException.class, () -> status(r.id(), "in_progress", null));
        assertTrue(ex.getMessage().contains("closed"));
        assertThrows(InvalidStatusException.class, () -> assign(r.id(), electrician));
    }

    // ---------- queue, aging and dashboard ----------

    @Test
    void agingGrowsWithTheDaysAnActiveRequestHasWaited() {
        RequestResponse fresh = raise("res1", "plumbing", "A", "low");
        RequestResponse aging = raise("res1", "plumbing", "B", "low");
        RequestResponse stale = raise("res1", "plumbing", "C", "low");
        backdate(aging.id(), 72);
        backdate(stale.id(), 24 * 6);
        loginAs("mgr", Role.FACILITY_MANAGER);
        List<RequestResponse> all = service.queue(null, null, null, null, null, null);
        assertEquals("FRESH", all.stream().filter(r -> r.id().equals(fresh.id())).findFirst().orElseThrow().aging());
        assertEquals("AGING", all.stream().filter(r -> r.id().equals(aging.id())).findFirst().orElseThrow().aging());
        RequestResponse staleOne = all.stream().filter(r -> r.id().equals(stale.id())).findFirst().orElseThrow();
        assertEquals("STALE", staleOne.aging());
        assertEquals(6, staleOne.ageDays());
        assertEquals(List.of(stale.id()), service.queue(null, null, null, null, "stale", null).stream().map(RequestResponse::id).toList());
    }

    @Test
    void queuePutsEmergenciesFirstThenTheOldestAndFinishedLast() {
        RequestResponse lowOld = raise("res1", "garden", "Old", "low");
        RequestResponse emergency = raise("res1", "lift", "New emergency", "emergency");
        RequestResponse highOld = raise("res2", "plumbing", "High old", "high");
        RequestResponse done = raise("res2", "plumbing", "Done", "emergency");
        backdate(lowOld.id(), 200);
        backdate(highOld.id(), 100);
        assign(done.id(), plumber);
        finish(done.id());
        loginAs("mgr", Role.FACILITY_MANAGER);
        assertEquals(List.of(emergency.id(), highOld.id(), lowOld.id(), done.id()),
                service.queue(null, null, null, null, null, null).stream().map(RequestResponse::id).toList());
    }

    @Test
    void queueFiltersByStatusPriorityCategoryTechnicianAndText() {
        RequestResponse a = raise("res1", "plumbing", "Block A", "high");
        RequestResponse b = raise("res2", "garden", "Park", "low");
        assign(a.id(), plumber);
        loginAs("mgr", Role.FACILITY_MANAGER);
        assertEquals(1, service.queue(RequestStatus.OPEN, null, null, null, null, null).size());
        assertEquals(1, service.queue(null, Priority.HIGH, null, null, null, null).size());
        assertEquals(List.of(b.id()), service.queue(null, null, Category.GARDEN, null, null, null).stream().map(RequestResponse::id).toList());
        assertEquals(List.of(a.id()), service.queue(null, null, null, plumber.getId(), null, null).stream().map(RequestResponse::id).toList());
        assertEquals(1, service.queue(null, null, null, null, null, "resident two").size());
        assertEquals(1, service.queue(null, null, null, null, null, a.number()).size());
    }

    @Test
    void dashboardCountsStatusesAgingCategoriesAndTechnicianLoad() {
        RequestResponse a = raise("res1", "plumbing", "A", "emergency");
        RequestResponse b = raise("res1", "plumbing", "B", "low");
        RequestResponse c = raise("res2", "garden", "C", "low");
        backdate(a.id(), 24 * 6);
        assign(b.id(), plumber);
        assign(c.id(), electrician);
        finish(c.id());
        loginAs("mgr", Role.FACILITY_MANAGER);
        Dashboard d = service.dashboard();
        assertEquals(1, d.open());
        assertEquals(1, d.assigned());
        assertEquals(1, d.resolved());
        assertEquals(1, d.stale());
        assertEquals(1, d.fresh());
        assertEquals(1, d.emergencies());
        assertEquals(8, d.byCategory().size());
        assertEquals("PLUMBING", d.byCategory().get(0).category());
        assertEquals(2, d.byCategory().get(0).total());
        assertEquals(2, d.byCategory().get(0).active());
        assertEquals(1, d.technicianLoad().stream().filter(t -> t.name().equals("Ramesh")).findFirst().orElseThrow().activeJobs());
        assertFalse(d.averageResolutionHours() < 0);
    }

    // ---------- technicians ----------

    @Test
    void managersAddTechniciansWithUniqueNames() {
        loginAs("mgr", Role.FACILITY_MANAGER);
        TechnicianResponse added = service.addTechnician(new TechnicianBody("  Geeta Crew  ", "cleaning", "+91 98450 00000"));
        assertEquals("Geeta Crew", added.name());
        assertEquals("CLEANING", added.trade());
        assertEquals("name", assertThrows(FieldValidationException.class, () -> service.addTechnician(new TechnicianBody("geeta crew", "cleaning", null))).getField());
        assertEquals("trade", assertThrows(FieldValidationException.class, () -> service.addTechnician(new TechnicianBody("X", "witchcraft", null))).getField());
    }

    @Test
    void aTechnicianWithActiveJobsCannotBeDeactivated() {
        RequestResponse r = raise("res1", "plumbing", "A", "low");
        assign(r.id(), plumber);
        loginAs("mgr", Role.FACILITY_MANAGER);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.setActive(plumber.getId(), false));
        assertTrue(ex.getMessage().contains("1 active job"));
        assertFalse(service.setActive(electrician.getId(), false).active());
        assertTrue(service.setActive(retired.getId(), true).active());
    }

    @Test
    void rolesAreEnforced() {
        loginAs("res1", Role.RESIDENT);
        assertThrows(AccessDeniedException.class, () -> service.queue(null, null, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.assign(1L, 1L));
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(1L, "in_progress", null));
        assertThrows(AccessDeniedException.class, () -> service.dashboard());
        assertThrows(AccessDeniedException.class, () -> service.technicians());
        loginAs("mgr", Role.FACILITY_MANAGER);
        assertThrows(AccessDeniedException.class, () -> service.raise("mgr", new NewRequest("plumbing", "d", "l", "low")));
        assertThrows(AccessDeniedException.class, () -> service.mine("mgr", null, null));
    }

    // ---------- concurrency ----------

    @Test
    void twoManagersStartingTheSameAssignedRequestStartItOnce() throws Exception {
        RequestResponse r = raise("res1", "plumbing", "A", "high");
        assign(r.id(), plumber);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("mgr", Role.FACILITY_MANAGER);
                try {
                    service.updateStatus(r.id(), "in_progress", null);
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
    void twoManagersResolvingTheSameRequestResolveItOnce() throws Exception {
        RequestResponse r = raise("res1", "plumbing", "A", "high");
        assign(r.id(), plumber);
        status(r.id(), "in_progress", null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 2).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("mgr", Role.FACILITY_MANAGER);
                try {
                    service.updateStatus(r.id(), "resolved", "Fixed " + i);
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
