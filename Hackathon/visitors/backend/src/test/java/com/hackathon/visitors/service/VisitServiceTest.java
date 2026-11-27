package com.hackathon.visitors.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
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

import com.hackathon.visitors.dto.DailyStats;
import com.hackathon.visitors.dto.VisitResponse;
import com.hackathon.visitors.dto.VisitorRequest;
import com.hackathon.visitors.entity.Role;
import com.hackathon.visitors.entity.User;
import com.hackathon.visitors.entity.Visit;
import com.hackathon.visitors.entity.VisitStatus;
import com.hackathon.visitors.exception.BusinessRuleException;
import com.hackathon.visitors.exception.ConflictException;
import com.hackathon.visitors.exception.FieldValidationException;
import com.hackathon.visitors.exception.ForbiddenActionException;
import com.hackathon.visitors.exception.InvalidStatusException;
import com.hackathon.visitors.exception.ResourceNotFoundException;
import com.hackathon.visitors.repo.UserRepository;
import com.hackathon.visitors.repo.VisitRepository;

@SpringBootTest
@ActiveProfiles("test")
class VisitServiceTest {

    @Autowired VisitService service;
    @Autowired VisitRepository visits;
    @Autowired UserRepository users;

    LocalDate today;

    @BeforeEach
    void setUp() {
        visits.deleteAll();
        users.deleteAll();
        users.save(new User("guard", "x", "Guard One", Role.SECURITY_ADMIN));
        users.save(new User("guard2", "x", "Guard Two", Role.SECURITY_ADMIN));
        users.save(new User("emp1", "x", "Employee One", Role.EMPLOYEE));
        users.save(new User("emp2", "x", "Employee Two", Role.EMPLOYEE));
        today = LocalDate.now();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    VisitorRequest request(String name, String phone, LocalDate date) {
        return new VisitorRequest(name, phone, "Acme Ltd", "Client meeting", date, LocalTime.of(11, 0));
    }

    VisitResponse invite(String employee, String name, String phone, LocalDate date) {
        loginAs(employee, Role.EMPLOYEE);
        return service.invite(employee, request(name, phone, date));
    }

    VisitResponse checkIn(Long id) {
        loginAs("guard", Role.SECURITY_ADMIN);
        return service.checkIn(id, "guard");
    }

    VisitResponse checkOut(Long id) {
        loginAs("guard", Role.SECURITY_ADMIN);
        return service.checkOut(id, "guard");
    }

    /** Moves an invitation's expected date by editing it directly, as if the day had passed. */
    void setDate(Long id, LocalDate date) {
        Visit v = visits.findById(id).orElseThrow();
        v.update(v.getVisitorName(), v.getPhone(), v.getCompany(), v.getPurpose(), date, v.getExpectedTime());
        visits.save(v);
    }

    // ---------- inviting ----------

    @Test
    void employeeInvitesAVisitorAndGetsAPassCode() {
        VisitResponse v = invite("emp1", "  Rakesh Bansal  ", "+91 98450-11201", today);
        assertEquals("EXPECTED", v.status());
        assertEquals("Rakesh Bansal", v.visitorName());
        assertEquals("+919845011201", v.phone());
        assertTrue(v.passCode().matches("VP-\\d{6}"));
        assertTrue(v.dueToday());
        assertTrue(v.canCheckIn());
        assertTrue(v.canCancel());
        assertFalse(v.canCheckOut());
        assertEquals("Employee One", v.hostName());
    }

    @Test
    void nameAndPhoneAreMandatoryAndPastDatesAreRefused() {
        loginAs("emp1", Role.EMPLOYEE);
        FieldValidationException past = assertThrows(FieldValidationException.class, () -> service.invite("emp1", request("A", "9845011201", today.minusDays(1))));
        assertEquals("expectedDate", past.getField());
        FieldValidationException shortPhone = assertThrows(FieldValidationException.class, () -> service.invite("emp1", request("A", "12 34", today)));
        assertEquals("phone", shortPhone.getField());
        assertNotNull(service.invite("emp1", request("A", "9845011201", today)).id()); // today is fine
    }

    @Test
    void invitingTheSamePhoneForTheSameDayTwiceIsAConflictButOtherDaysAreFine() {
        invite("emp1", "Rakesh", "9845011201", today);
        assertThrows(ConflictException.class, () -> invite("emp1", "Rakesh again", "98450 11201", today));
        assertNotNull(invite("emp1", "Rakesh", "9845011201", today.plusDays(1)).id());
        assertNotNull(invite("emp2", "Rakesh", "9845011201", today).id()); // another host may invite the same person
    }

    @Test
    void aCancelledInvitationDoesNotBlockANewOne() {
        VisitResponse first = invite("emp1", "Rakesh", "9845011201", today);
        loginAs("emp1", Role.EMPLOYEE);
        service.cancel(first.id(), "emp1");
        assertNotNull(invite("emp1", "Rakesh", "9845011201", today).id());
    }

    // ---------- employee views and changes ----------

    @Test
    void employeesSeeOnlyTheirOwnInvitationsAndCanFilter() {
        invite("emp1", "Alpha", "9845011201", today);
        VisitResponse b = invite("emp1", "Beta", "9845011202", today.plusDays(1));
        invite("emp2", "Gamma", "9845011203", today);
        checkIn(visits.findAll().stream().filter(v -> v.getVisitorName().equals("Alpha")).findFirst().orElseThrow().getId());
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals(2, service.mine("emp1", null, null).size());
        assertEquals(1, service.mine("emp1", VisitStatus.CHECKED_IN, null).size());
        assertEquals(1, service.mine("emp1", null, "beta").size());
        assertEquals(1, service.mine("emp1", null, b.passCode()).size());
        assertEquals(1, service.mine("emp1", null, "98450 11202").size());
        loginAs("emp2", Role.EMPLOYEE);
        assertEquals(1, service.mine("emp2", null, null).size());
    }

    @Test
    void anInvitationCanBeChangedUntilItIsUsed() {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today.plusDays(2));
        loginAs("emp1", Role.EMPLOYEE);
        VisitResponse changed = service.update(v.id(), "emp1", new VisitorRequest("Rakesh B", "9845011299", null, "Contract signing", today.plusDays(3), null));
        assertEquals("Rakesh B", changed.visitorName());
        assertEquals(today.plusDays(3), changed.expectedDate());
        assertNull(changed.company());
        assertEquals(v.passCode(), changed.passCode());
        assertThrows(FieldValidationException.class, () -> service.update(v.id(), "emp1", request("X", "9845011299", today.minusDays(1))));
        loginAs("emp2", Role.EMPLOYEE);
        assertThrows(ForbiddenActionException.class, () -> service.update(v.id(), "emp2", request("X", "9845011299", today)));
        assertThrows(ForbiddenActionException.class, () -> service.cancel(v.id(), "emp2"));
    }

    @Test
    void anUsedInvitationCanNoLongerBeChangedOrCancelled() {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        checkIn(v.id());
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(InvalidStatusException.class, () -> service.cancel(v.id(), "emp1"));
        assertThrows(InvalidStatusException.class, () -> service.update(v.id(), "emp1", request("X", "9845011201", today)));
    }

    // ---------- the gate ----------

    @Test
    void securityChecksVisitorsInAndOutAndSeesHowLongTheyAreInside() {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        VisitResponse in = checkIn(v.id());
        assertEquals("CHECKED_IN", in.status());
        assertEquals("guard", in.checkedInBy());
        assertNotNull(in.checkedInAt());
        assertTrue(in.canCheckOut());
        assertFalse(in.canCheckIn());
        assertEquals(0L, in.minutesInside());
        VisitResponse out = checkOut(v.id());
        assertEquals("CHECKED_OUT", out.status());
        assertNotNull(out.checkedOutAt());
        assertNull(out.minutesInside());
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals("CHECKED_OUT", service.mine("emp1", null, null).get(0).status()); // the host sees it
    }

    @Test
    void aVisitorCannotBeCheckedOutBeforeCheckIn() {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        InvalidStatusException ex = assertThrows(InvalidStatusException.class, () -> checkOut(v.id()));
        assertTrue(ex.getMessage().contains("not been checked in"));
    }

    @Test
    void doubleCheckInAndDoubleCheckOutAreRefused() {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        checkIn(v.id());
        assertTrue(assertThrows(InvalidStatusException.class, () -> checkIn(v.id())).getMessage().contains("already inside"));
        checkOut(v.id());
        assertTrue(assertThrows(InvalidStatusException.class, () -> checkOut(v.id())).getMessage().contains("already left"));
        assertTrue(assertThrows(InvalidStatusException.class, () -> checkIn(v.id())).getMessage().contains("already left"));
    }

    @Test
    void onlyTodaysVisitorsCanBeCheckedIn() {
        VisitResponse future = invite("emp1", "Future", "9845011201", today.plusDays(2));
        BusinessRuleException early = assertThrows(BusinessRuleException.class, () -> checkIn(future.id()));
        assertTrue(early.getMessage().contains("not today"));
        VisitResponse old = invite("emp1", "Old", "9845011202", today);
        setDate(old.id(), today.minusDays(1));
        BusinessRuleException late = assertThrows(BusinessRuleException.class, () -> checkIn(old.id()));
        assertTrue(late.getMessage().contains("send a new one"));
    }

    @Test
    void aCancelledInvitationCannotBeCheckedInAndUnknownVisitsAre404() {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        loginAs("emp1", Role.EMPLOYEE);
        service.cancel(v.id(), "emp1");
        assertTrue(assertThrows(InvalidStatusException.class, () -> checkIn(v.id())).getMessage().contains("cancelled"));
        assertThrows(ResourceNotFoundException.class, () -> checkIn(99999L));
    }

    @Test
    void gateListDefaultsToTodayAndOrdersInsideFirstThenByTime() {
        VisitResponse late = invite("emp1", "Late", "9845011201", today);
        VisitResponse inside = invite("emp1", "Inside", "9845011202", today);
        VisitResponse tomorrow = invite("emp2", "Tomorrow", "9845011203", today.plusDays(1));
        VisitResponse done = invite("emp2", "Done", "9845011204", today);
        checkIn(inside.id());
        checkIn(done.id());
        checkOut(done.id());
        loginAs("guard", Role.SECURITY_ADMIN);
        assertEquals(List.of(inside.id(), late.id(), done.id()), service.gateList(null, null, null).stream().map(VisitResponse::id).toList());
        assertEquals(List.of(tomorrow.id()), service.gateList(today.plusDays(1), null, null).stream().map(VisitResponse::id).toList());
        assertEquals(List.of(late.id()), service.gateList(null, VisitStatus.EXPECTED, null).stream().map(VisitResponse::id).toList());
    }

    @Test
    void searchingWithoutADateLooksAcrossAllDaysByNamePhonePassOrHost() {
        VisitResponse a = invite("emp1", "Rakesh Bansal", "9845011201", today);
        invite("emp2", "Sunita Verma", "9845011202", today.plusDays(4));
        loginAs("guard", Role.SECURITY_ADMIN);
        assertEquals(1, service.gateList(null, null, "sunita").size());
        assertEquals(1, service.gateList(null, null, "98450 11202").size());
        assertEquals(1, service.gateList(null, null, a.passCode()).size());
        assertEquals(1, service.gateList(null, null, "employee two").size());
        assertEquals(0, service.gateList(null, null, "nobody").size());
        assertEquals(a.id(), service.byPass(" " + a.passCode().toLowerCase() + " ").id());
        assertThrows(ResourceNotFoundException.class, () -> service.byPass("VP-000000"));
    }

    @Test
    void statsCountTodayInsideUpcomingAndNoShows() {
        VisitResponse a = invite("emp1", "A", "9845011201", today);
        VisitResponse b = invite("emp1", "B", "9845011202", today);
        invite("emp2", "C", "9845011203", today);
        VisitResponse gone = invite("emp2", "D", "9845011204", today);
        invite("emp2", "E", "9845011205", today.plusDays(3));
        VisitResponse noShow = invite("emp2", "F", "9845011206", today);
        setDate(noShow.id(), today.minusDays(1));
        checkIn(a.id());
        checkIn(b.id());
        checkIn(gone.id());
        checkOut(gone.id());
        loginAs("emp2", Role.EMPLOYEE);
        VisitResponse cancelled = service.invite("emp2", request("G", "9845011207", today));
        service.cancel(cancelled.id(), "emp2");
        loginAs("guard", Role.SECURITY_ADMIN);
        DailyStats s = service.stats();
        assertEquals(4, s.expectedToday());
        assertEquals(1, s.notYetArrived());
        assertEquals(2, s.insideNow());
        assertEquals(1, s.checkedOutToday());
        assertEquals(1, s.cancelledToday());
        assertEquals(1, s.upcomingNextSevenDays());
        assertEquals(1, s.noShowsYesterday());
        assertEquals(7, s.lastSevenDays().size());
        assertEquals("Employee One", s.busiestHostsToday().get(0).host()); // two each, ties by name
        assertEquals(2, s.busiestHostsToday().get(0).visitors());
        assertEquals(today, s.date());
    }

    @Test
    void lapsedInvitationsAreFlagged() {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        setDate(v.id(), today.minusDays(2));
        loginAs("emp1", Role.EMPLOYEE);
        VisitResponse lapsed = service.mine("emp1", null, null).get(0);
        assertTrue(lapsed.lapsed());
        assertFalse(lapsed.dueToday());
        assertFalse(lapsed.canCheckIn());
    }

    @Test
    void rolesAreEnforced() {
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> service.gateList(null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.checkIn(1L, "emp1"));
        assertThrows(AccessDeniedException.class, () -> service.checkOut(1L, "emp1"));
        assertThrows(AccessDeniedException.class, () -> service.stats());
        assertThrows(AccessDeniedException.class, () -> service.byPass("VP-123456"));
        loginAs("guard", Role.SECURITY_ADMIN);
        assertThrows(AccessDeniedException.class, () -> service.invite("guard", request("A", "9845011201", today)));
        assertThrows(AccessDeniedException.class, () -> service.mine("guard", null, null));
        assertThrows(AccessDeniedException.class, () -> service.cancel(1L, "guard"));
    }

    // ---------- concurrency ----------

    @Test
    void twoGuardsCheckingTheSameVisitorInCheckThemInOnce() throws Exception {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs(i % 2 == 0 ? "guard" : "guard2", Role.SECURITY_ADMIN);
                try {
                    service.checkIn(v.id(), i % 2 == 0 ? "guard" : "guard2");
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
    void aDoubleClickedInvitationCreatesOnePass() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("emp1", Role.EMPLOYEE);
                try {
                    service.invite("emp1", request("Rakesh", "9845011201", today));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, visits.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellingWhileSecurityChecksInEndsInOneConsistentState() throws Exception {
        VisitResponse v = invite("emp1", "Rakesh", "9845011201", today);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> cancel = () -> {
                loginAs("emp1", Role.EMPLOYEE);
                try {
                    service.cancel(v.id(), "emp1");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Callable<Boolean> arrive = () -> {
                loginAs("guard", Role.SECURITY_ADMIN);
                try {
                    service.checkIn(v.id(), "guard");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            assertEquals(1, winners(pool.invokeAll(List.of(cancel, arrive))));
            VisitStatus end = visits.findById(v.id()).orElseThrow().getStatus();
            assertTrue(end == VisitStatus.CANCELLED || end == VisitStatus.CHECKED_IN);
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
