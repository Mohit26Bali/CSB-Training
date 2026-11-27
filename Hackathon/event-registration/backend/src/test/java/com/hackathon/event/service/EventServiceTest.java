package com.hackathon.event.service;

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

import com.hackathon.event.dto.EventDashboard;
import com.hackathon.event.dto.EventRequest;
import com.hackathon.event.dto.EventResponse;
import com.hackathon.event.dto.RegistrationRequest;
import com.hackathon.event.dto.RegistrationResponse;
import com.hackathon.event.entity.Event;
import com.hackathon.event.entity.EventStatus;
import com.hackathon.event.entity.Registration;
import com.hackathon.event.entity.RegistrationStatus;
import com.hackathon.event.entity.Role;
import com.hackathon.event.entity.User;
import com.hackathon.event.exception.BusinessRuleException;
import com.hackathon.event.exception.ConflictException;
import com.hackathon.event.exception.FieldValidationException;
import com.hackathon.event.exception.ForbiddenActionException;
import com.hackathon.event.exception.InvalidStatusException;
import com.hackathon.event.repo.EventRepository;
import com.hackathon.event.repo.RegistrationRepository;
import com.hackathon.event.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class EventServiceTest {

    @Autowired EventService eventService;
    @Autowired RegistrationService registrationService;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired UserRepository users;

    Event open;
    Event small;
    Event draft;
    Event past;

    @BeforeEach
    void setUp() {
        registrations.deleteAll();
        events.deleteAll();
        users.deleteAll();
        users.save(new User("admin", "x", "Admin", Role.EVENT_ADMIN));
        for (int i = 1; i <= 8; i++) {
            users.save(new User("emp" + i, "x", "Employee " + i, Role.EMPLOYEE));
        }
        open = published("Cloud Talk", 10, 3);
        small = published("Yoga", 2, 1);
        draft = events.save(newEvent("Hackathon", 5, 3));
        past = published("Old Talk", 10, -2);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    Event newEvent(String title, int capacity, int daysFromNow) {
        LocalDateTime start = LocalDateTime.now().plusDays(daysFromNow);
        return new Event(title, "Workshop", "Room 1", "Details", start, start.plusHours(2), capacity, "admin");
    }

    Event published(String title, int capacity, int daysFromNow) {
        Event e = newEvent(title, capacity, daysFromNow);
        e.publish();
        return events.save(e);
    }

    RegistrationResponse register(String user, Event event, boolean waitlist) {
        loginAs(user, Role.EMPLOYEE);
        return registrationService.register(user, new RegistrationRequest(event.getId(), waitlist));
    }

    EventRequest request(int capacity, int daysFromNow) {
        LocalDateTime start = LocalDateTime.now().plusDays(daysFromNow);
        return new EventRequest("Quarterly Review", "Town Hall", "Hall", "Review", start, start.plusHours(1), capacity);
    }

    // ---------- registering ----------

    @Test
    void employeeRegistersAndSeesItInTheirList() {
        RegistrationResponse r = register("emp1", open, false);
        assertEquals("CONFIRMED", r.status());
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals(1, registrationService.mine("emp1").size());
        EventResponse listed = eventService.upcoming("emp1", null, null).stream().filter(e -> e.id().equals(open.getId())).findFirst().orElseThrow();
        assertEquals("CONFIRMED", listed.myStatus());
        assertEquals(1, listed.confirmed());
        assertEquals(9, listed.spotsLeft());
    }

    @Test
    void duplicateRegistrationIsRefused() {
        register("emp1", open, false);
        assertThrows(ConflictException.class, () -> register("emp1", open, false));
    }

    @Test
    void pastEventsDoNotAcceptRegistration() {
        assertThrows(BusinessRuleException.class, () -> register("emp1", past, false));
    }

    @Test
    void draftEventsDoNotAcceptRegistration() {
        assertThrows(BusinessRuleException.class, () -> register("emp1", draft, false));
    }

    @Test
    void unknownEventIsAFieldError() {
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(FieldValidationException.class, () -> registrationService.register("emp1", new RegistrationRequest(9999L, false)));
    }

    @Test
    void fullEventIsBlockedUnlessTheEmployeeJoinsTheWaitlist() {
        register("emp1", small, false);
        register("emp2", small, false);
        ConflictException full = assertThrows(ConflictException.class, () -> register("emp3", small, false));
        assertTrue(full.getMessage().contains("full"));
        RegistrationResponse waiting = register("emp3", small, true);
        assertEquals("WAITLISTED", waiting.status());
        assertEquals(1, waiting.waitlistPosition());
        assertEquals(2, register("emp4", small, true).waitlistPosition());
        assertThrows(ConflictException.class, () -> register("emp3", small, true)); // already waiting
    }

    @Test
    void cancellingPromotesTheFirstPersonOnTheWaitlist() {
        RegistrationResponse first = register("emp1", small, false);
        register("emp2", small, false);
        register("emp3", small, true);
        register("emp4", small, true);

        loginAs("emp1", Role.EMPLOYEE);
        registrationService.cancel("emp1", first.id());

        loginAs("emp3", Role.EMPLOYEE);
        assertEquals("CONFIRMED", registrationService.mine("emp3").get(0).status());
        loginAs("emp4", Role.EMPLOYEE);
        RegistrationResponse still = registrationService.mine("emp4").get(0);
        assertEquals("WAITLISTED", still.status());
        assertEquals(1, still.waitlistPosition());
    }

    @Test
    void cancelledEmployeeCanRegisterAgainWithoutBreakingTheUniqueRule() {
        RegistrationResponse r = register("emp1", open, false);
        loginAs("emp1", Role.EMPLOYEE);
        registrationService.cancel("emp1", r.id());
        assertEquals("CONFIRMED", register("emp1", open, false).status());
        assertEquals(1, registrations.count());
    }

    @Test
    void onlyTheOwnerCanCancelAndOnlyOnce() {
        RegistrationResponse r = register("emp1", open, false);
        loginAs("emp2", Role.EMPLOYEE);
        assertThrows(ForbiddenActionException.class, () -> registrationService.cancel("emp2", r.id()));
        loginAs("emp1", Role.EMPLOYEE);
        registrationService.cancel("emp1", r.id());
        assertThrows(ConflictException.class, () -> registrationService.cancel("emp1", r.id()));
    }

    @Test
    void cannotCancelOnceTheEventHasStarted() {
        Registration r = registrations.save(new Registration(past, users.findByUsername("emp1").orElseThrow(), RegistrationStatus.CONFIRMED));
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(BusinessRuleException.class, () -> registrationService.cancel("emp1", r.getId()));
    }

    // ---------- admin ----------

    @Test
    void adminCreatesDraftThenPublishes() {
        loginAs("admin", Role.EVENT_ADMIN);
        EventResponse created = eventService.create(request(30, 7), "admin");
        assertEquals("DRAFT", created.status());
        assertEquals("PUBLISHED", eventService.publish(created.id()).status());
        assertThrows(InvalidStatusException.class, () -> eventService.publish(created.id()));
    }

    @Test
    void createValidatesDatesAndCategory() {
        loginAs("admin", Role.EVENT_ADMIN);
        FieldValidationException pastStart = assertThrows(FieldValidationException.class, () -> eventService.create(request(30, -1), "admin"));
        assertEquals("startsAt", pastStart.getField());
        LocalDateTime start = LocalDateTime.now().plusDays(3);
        assertThrows(FieldValidationException.class, () -> eventService.create(
                new EventRequest("x", "Workshop", "v", "d", start, start.minusHours(1), 5), "admin"));
        assertThrows(FieldValidationException.class, () -> eventService.create(
                new EventRequest("x", "Nonsense", "v", "d", start, start.plusHours(1), 5), "admin"));
    }

    @Test
    void capacityCannotDropBelowConfirmedAndRaisingItPromotesTheWaitlist() {
        register("emp1", small, false);
        register("emp2", small, false);
        register("emp3", small, true);
        loginAs("admin", Role.EVENT_ADMIN);
        FieldValidationException tooLow = assertThrows(FieldValidationException.class, () -> eventService.update(small.getId(), request(1, 5)));
        assertEquals("capacity", tooLow.getField());
        EventResponse updated = eventService.update(small.getId(), request(3, 5));
        assertEquals(3, updated.confirmed());
        assertEquals(0, updated.waitlisted());
    }

    @Test
    void cancellingAnEventCancelsItsRegistrations() {
        register("emp1", open, false);
        register("emp2", open, false);
        loginAs("admin", Role.EVENT_ADMIN);
        assertEquals("CANCELLED", eventService.cancel(open.getId()).status());
        assertEquals(0, eventService.participants(open.getId()).size());
        assertThrows(InvalidStatusException.class, () -> eventService.cancel(open.getId()));
        assertThrows(BusinessRuleException.class, () -> register("emp3", open, false));
    }

    @Test
    void startedEventsCannotBeEditedOrCancelled() {
        loginAs("admin", Role.EVENT_ADMIN);
        assertThrows(BusinessRuleException.class, () -> eventService.update(past.getId(), request(5, 4)));
        assertThrows(BusinessRuleException.class, () -> eventService.cancel(past.getId()));
    }

    @Test
    void participantsListConfirmedFirstThenWaitlistInOrder() {
        register("emp1", small, false);
        register("emp2", small, false);
        register("emp3", small, true);
        register("emp4", small, true);
        loginAs("admin", Role.EVENT_ADMIN);
        List<RegistrationResponse> list = eventService.participants(small.getId());
        assertEquals(List.of("CONFIRMED", "CONFIRMED", "WAITLISTED", "WAITLISTED"), list.stream().map(RegistrationResponse::status).toList());
        assertEquals(List.of("emp3", "emp4"), list.stream().filter(p -> p.waitlistPosition() != null).map(RegistrationResponse::username).toList());
        assertNull(list.get(0).waitlistPosition());
    }

    @Test
    void dashboardAndFiltersUseTheData() {
        register("emp1", open, false);
        register("emp2", small, false);
        register("emp3", small, false);
        register("emp4", small, true);
        loginAs("admin", Role.EVENT_ADMIN);
        EventDashboard d = eventService.dashboard();
        assertEquals(4, d.totalEvents());
        assertEquals(2, d.upcomingPublished());
        assertEquals(3, d.confirmedSeats());
        assertEquals(1, d.waitlisted());
        assertEquals(100, d.events().stream().filter(e -> e.id().equals(small.getId())).findFirst().orElseThrow().fillPercent());

        assertEquals(1, eventService.upcoming("emp1", "yoga", null).size());
        assertEquals(2, eventService.upcoming("emp1", null, "Workshop").size());
        assertEquals(0, eventService.upcoming("emp1", null, "Sports").size());
        assertEquals(2, eventService.upcoming("emp1", null, null).size()); // draft and past events are hidden
    }

    @Test
    void rolesAreEnforcedOnTheServices() {
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> eventService.create(request(5, 5), "emp1"));
        assertThrows(AccessDeniedException.class, () -> eventService.dashboard());
        loginAs("admin", Role.EVENT_ADMIN);
        assertThrows(AccessDeniedException.class, () -> registrationService.register("admin", new RegistrationRequest(open.getId(), false)));
    }

    // ---------- concurrency ----------

    @Test
    void lastSeatGoesToExactlyOnePersonWhenManyRegisterAtOnce() throws Exception {
        Event oneSeat = published("Last Seat", 1, 4);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = IntStream.rangeClosed(1, 8).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("emp" + i, Role.EMPLOYEE);
                try {
                    registrationService.register("emp" + i, new RegistrationRequest(oneSeat.getId(), false));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertEquals(1, registrations.countByEventAndStatus(events.findById(oneSeat.getId()).orElseThrow(), RegistrationStatus.CONFIRMED));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameEmployeeDoubleClickingCreatesOneRegistration() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("emp1", Role.EMPLOYEE);
                try {
                    registrationService.register("emp1", new RegistrationRequest(open.getId(), false));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertEquals(1, registrations.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoCancellationsFillTwoSeatsFromTheWaitlistWithoutOvershooting() throws Exception {
        RegistrationResponse a = register("emp1", small, false);
        RegistrationResponse b = register("emp2", small, false);
        register("emp3", small, true);
        register("emp4", small, true);
        register("emp5", small, true);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> cancelAs("emp1", a.id()),
                    () -> cancelAs("emp2", b.id()));
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                assertTrue(f.get());
            }
            Event reloaded = events.findById(small.getId()).orElseThrow();
            assertEquals(2, registrations.countByEventAndStatus(reloaded, RegistrationStatus.CONFIRMED));
            assertEquals(1, registrations.countByEventAndStatus(reloaded, RegistrationStatus.WAITLISTED));
            assertEquals(EventStatus.PUBLISHED, reloaded.getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean cancelAs(String user, Long registrationId) {
        loginAs(user, Role.EMPLOYEE);
        try {
            registrationService.cancel(user, registrationId);
            return true;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
