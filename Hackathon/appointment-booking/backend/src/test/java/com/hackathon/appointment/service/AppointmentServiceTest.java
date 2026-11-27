package com.hackathon.appointment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.hackathon.appointment.dto.AppointmentRequest;
import com.hackathon.appointment.dto.AppointmentResponse;
import com.hackathon.appointment.dto.BulkSlotBody;
import com.hackathon.appointment.dto.ProfileBody;
import com.hackathon.appointment.dto.ProviderDashboard;
import com.hackathon.appointment.dto.ProviderResponse;
import com.hackathon.appointment.dto.SlotBody;
import com.hackathon.appointment.dto.SlotResponse;
import com.hackathon.appointment.entity.AppointmentStatus;
import com.hackathon.appointment.entity.Provider;
import com.hackathon.appointment.entity.Role;
import com.hackathon.appointment.entity.Slot;
import com.hackathon.appointment.entity.User;
import com.hackathon.appointment.exception.BusinessRuleException;
import com.hackathon.appointment.exception.ConflictException;
import com.hackathon.appointment.exception.FieldValidationException;
import com.hackathon.appointment.exception.ForbiddenActionException;
import com.hackathon.appointment.exception.InvalidStatusException;
import com.hackathon.appointment.repo.AppointmentRepository;
import com.hackathon.appointment.repo.ProviderRepository;
import com.hackathon.appointment.repo.SlotRepository;
import com.hackathon.appointment.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class AppointmentServiceTest {

    @Autowired AppointmentService service;
    @Autowired ProviderRepository providers;
    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired UserRepository users;

    Provider dentist;
    Provider physio;
    LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        appointments.deleteAll();
        slots.deleteAll();
        providers.deleteAll();
        users.deleteAll();
        tomorrow = LocalDate.now().plusDays(1);
        User p1 = users.save(new User("prov1", "x", "Dr. Dent", Role.SERVICE_PROVIDER));
        User p2 = users.save(new User("prov2", "x", "Pia Physio", Role.SERVICE_PROVIDER));
        for (int i = 1; i <= 5; i++) {
            users.save(new User("c" + i, "x", "Customer " + i, Role.CUSTOMER));
        }
        dentist = providers.save(new Provider(p1, "Dentist", "Teeth", "Pune"));
        physio = providers.save(new Provider(p2, "Physiotherapist", "Backs", "Mumbai"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    Slot slot(Provider provider, LocalDateTime start, int minutes) {
        return slots.save(new Slot(provider, start, start.plusMinutes(minutes)));
    }

    Slot tomorrowAt(Provider provider, int hour, int minute) {
        return slot(provider, tomorrow.atTime(hour, minute), 30);
    }

    AppointmentResponse book(String customer, Slot slot) {
        loginAs(customer, Role.CUSTOMER);
        return service.book(customer, new AppointmentRequest(slot.getId(), "Check-up"));
    }

    AppointmentResponse confirm(Provider provider, AppointmentResponse a) {
        String username = provider.getUser().getUsername();
        loginAs(username, Role.SERVICE_PROVIDER);
        return service.confirm(username, a.id());
    }

    // ---------- creating slots ----------

    @Test
    void aProviderCreatesASlotInTheFuture() {
        loginAs("prov1", Role.SERVICE_PROVIDER);
        SlotResponse s = service.createSlot("prov1", new SlotBody(tomorrow, LocalTime.of(9, 0), 45));
        assertEquals("AVAILABLE", s.status());
        assertEquals(45, s.minutes());
        assertEquals(tomorrow.atTime(9, 45), s.end());
    }

    @Test
    void slotsInThePastOrTooFarAheadOrAcrossMidnightAreRejected() {
        loginAs("prov1", Role.SERVICE_PROVIDER);
        FieldValidationException past = assertThrows(FieldValidationException.class,
                () -> service.createSlot("prov1", new SlotBody(LocalDate.now().minusDays(1), LocalTime.of(9, 0), 30)));
        assertEquals("date", past.getField());
        assertThrows(FieldValidationException.class, () -> service.createSlot("prov1", new SlotBody(LocalDate.now().plusDays(61), LocalTime.of(9, 0), 30)));
        FieldValidationException midnight = assertThrows(FieldValidationException.class,
                () -> service.createSlot("prov1", new SlotBody(tomorrow, LocalTime.of(23, 30), 60)));
        assertEquals("minutes", midnight.getField());
    }

    @Test
    void aProvidersSlotsCannotOverlapButMayTouch() {
        loginAs("prov1", Role.SERVICE_PROVIDER);
        service.createSlot("prov1", new SlotBody(tomorrow, LocalTime.of(9, 0), 30));
        assertThrows(ConflictException.class, () -> service.createSlot("prov1", new SlotBody(tomorrow, LocalTime.of(9, 15), 30)));
        assertThrows(ConflictException.class, () -> service.createSlot("prov1", new SlotBody(tomorrow, LocalTime.of(8, 45), 30)));
        service.createSlot("prov1", new SlotBody(tomorrow, LocalTime.of(9, 30), 30)); // starts exactly when the first ends
        loginAs("prov2", Role.SERVICE_PROVIDER);
        service.createSlot("prov2", new SlotBody(tomorrow, LocalTime.of(9, 0), 30)); // another provider may use the same time
    }

    @Test
    void bulkCreationCutsAWindowIntoSlotsAllOrNothing() {
        loginAs("prov1", Role.SERVICE_PROVIDER);
        List<SlotResponse> made = service.createBulk("prov1", new BulkSlotBody(tomorrow, LocalTime.of(9, 0), LocalTime.of(11, 10), 30));
        assertEquals(4, made.size());
        assertEquals(tomorrow.atTime(10, 30), made.get(3).start());
        service.createSlot("prov1", new SlotBody(tomorrow, LocalTime.of(14, 30), 30));
        assertThrows(ConflictException.class, () -> service.createBulk("prov1", new BulkSlotBody(tomorrow, LocalTime.of(14, 0), LocalTime.of(16, 0), 30)));
        assertEquals(5, service.mySlots("prov1", tomorrow, 1).size()); // nothing from the failed batch
        assertThrows(FieldValidationException.class, () -> service.createBulk("prov1", new BulkSlotBody(tomorrow, LocalTime.of(10, 0), LocalTime.of(9, 0), 30)));
        assertThrows(FieldValidationException.class, () -> service.createBulk("prov1", new BulkSlotBody(tomorrow, LocalTime.of(12, 0), LocalTime.of(12, 20), 30)));
        FieldValidationException many = assertThrows(FieldValidationException.class, () -> service.createBulk("prov1", new BulkSlotBody(tomorrow, LocalTime.of(0, 15), LocalTime.of(23, 0), 15)));
        assertTrue(many.getMessage().contains("at most 24"));
    }

    @Test
    void withdrawingASlotNeedsItsAppointmentCancelledFirst() {
        Slot s = tomorrowAt(dentist, 9, 0);
        Slot free = tomorrowAt(dentist, 10, 0);
        AppointmentResponse a = book("c1", s);
        loginAs("prov2", Role.SERVICE_PROVIDER);
        assertThrows(ForbiddenActionException.class, () -> service.closeSlot("prov2", free.getId()));
        loginAs("prov1", Role.SERVICE_PROVIDER);
        assertThrows(ConflictException.class, () -> service.closeSlot("prov1", s.getId()));
        service.closeSlot("prov1", free.getId());
        assertThrows(InvalidStatusException.class, () -> service.closeSlot("prov1", free.getId()));
        service.cancelAsProvider("prov1", a.id(), "Emergency");
        service.closeSlot("prov1", s.getId());
        loginAs("c2", Role.CUSTOMER);
        assertEquals(0, service.providerSlots(dentist.getId(), tomorrow, 1).size());
        assertThrows(BusinessRuleException.class, () -> service.book("c2", new AppointmentRequest(s.getId(), null)));
    }

    // ---------- browsing ----------

    @Test
    void providersShowTheirFreeSlotsAndFirstFreeTime() {
        tomorrowAt(dentist, 9, 0);
        Slot taken = tomorrowAt(dentist, 9, 30);
        Slot closed = tomorrowAt(dentist, 10, 0);
        book("c1", taken);
        loginAs("prov1", Role.SERVICE_PROVIDER);
        service.closeSlot("prov1", closed.getId());
        slot(dentist, LocalDateTime.now().minusHours(2), 30); // already past
        loginAs("c2", Role.CUSTOMER);
        List<ProviderResponse> list = service.providers(null);
        ProviderResponse d = list.stream().filter(p -> p.title().equals("Dentist")).findFirst().orElseThrow();
        assertEquals(1, d.freeSlots());
        assertEquals(tomorrow.atTime(9, 0), d.nextFree());
        ProviderResponse p = list.stream().filter(x -> x.title().equals("Physiotherapist")).findFirst().orElseThrow();
        assertEquals(0, p.freeSlots());
        assertNull(p.nextFree());
        assertEquals(1, service.providers("dent").size());
        assertEquals(1, service.providers("mumbai").size());
        assertEquals(0, service.providers("zzz").size());
    }

    @Test
    void customersSeeWhichSlotsAreTakenButNotByWhom() {
        Slot a = tomorrowAt(dentist, 9, 0);
        tomorrowAt(dentist, 9, 30);
        book("c1", a);
        slot(dentist, LocalDateTime.now().minusMinutes(30), 30);
        loginAs("c2", Role.CUSTOMER);
        List<SlotResponse> view = service.providerSlots(dentist.getId(), null, 3);
        assertEquals(List.of("PENDING", "AVAILABLE"), view.stream().map(SlotResponse::status).toList());
        assertNull(view.get(0).customerName());
        assertNull(view.get(0).appointmentId());
        loginAs("prov1", Role.SERVICE_PROVIDER);
        SlotResponse mine = service.mySlots("prov1", tomorrow, 1).get(0);
        assertEquals("Customer 1", mine.customerName());
        assertNotNull(mine.appointmentId());
        assertThrows(FieldValidationException.class, () -> service.providerSlots(dentist.getId(), null, 40));
        assertThrows(com.hackathon.appointment.exception.ResourceNotFoundException.class, () -> service.providerSlots(999999L, null, 3));
    }

    // ---------- booking ----------

    @Test
    void bookingCreatesAPendingAppointment() {
        AppointmentResponse a = book("c1", tomorrowAt(dentist, 9, 0));
        assertEquals("PENDING", a.status());
        assertEquals("Dr. Dent", a.providerName());
        assertEquals("UPCOMING", a.phase());
        assertTrue(a.canCancel());
        assertTrue(a.canConfirm());
        assertTrue(a.number().startsWith("AP-"));
    }

    @Test
    void aPastOrClosedSlotCannotBeBooked() {
        Slot past = slot(dentist, LocalDateTime.now().minusMinutes(10), 30);
        loginAs("c1", Role.CUSTOMER);
        FieldValidationException e = assertThrows(FieldValidationException.class, () -> service.book("c1", new AppointmentRequest(past.getId(), null)));
        assertEquals("slotId", e.getField());
        assertThrows(com.hackathon.appointment.exception.ResourceNotFoundException.class, () -> service.book("c1", new AppointmentRequest(999999L, null)));
    }

    @Test
    void aSlotCannotBeDoubleBooked() {
        Slot s = tomorrowAt(dentist, 9, 0);
        book("c1", s);
        ConflictException e = assertThrows(ConflictException.class, () -> book("c2", s));
        assertTrue(e.getMessage().contains("just been taken"));
        loginAs("prov1", Role.SERVICE_PROVIDER);
        assertEquals(1, appointments.count());
    }

    @Test
    void aCustomerCannotHoldTwoAppointmentsAtTheSameTime() {
        book("c1", tomorrowAt(dentist, 9, 0));
        Slot clash = slot(physio, tomorrow.atTime(9, 15), 30);
        ConflictException e = assertThrows(ConflictException.class, () -> book("c1", clash));
        assertTrue(e.getMessage().contains("already have an appointment"));
        Slot adjacent = slot(physio, tomorrow.atTime(9, 30), 30);
        assertEquals("PENDING", book("c1", adjacent).status());
        assertEquals("PENDING", book("c2", clash).status());
    }

    @Test
    void providersCannotBookAndCustomersCannotRunTheCalendar() {
        Slot s = tomorrowAt(dentist, 9, 0);
        loginAs("prov1", Role.SERVICE_PROVIDER);
        assertThrows(AccessDeniedException.class, () -> service.book("prov1", new AppointmentRequest(s.getId(), null)));
        assertThrows(AccessDeniedException.class, () -> service.mine("prov1", null));
        loginAs("c1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> service.createSlot("c1", new SlotBody(tomorrow, LocalTime.of(9, 0), 30)));
        assertThrows(AccessDeniedException.class, () -> service.appointments("c1", null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.dashboard("c1"));
    }

    @Test
    void myAppointmentsAreSplitIntoUpcomingAndPrevious() {
        AppointmentResponse soon = book("c1", tomorrowAt(dentist, 9, 0));
        AppointmentResponse later = book("c1", slot(physio, tomorrow.plusDays(2).atTime(10, 0), 30));
        AppointmentResponse cancelled = book("c1", slot(dentist, tomorrow.plusDays(4).atTime(10, 0), 30));
        loginAs("c1", Role.CUSTOMER);
        service.cancelOwn("c1", cancelled.id(), null);
        assertEquals(List.of(soon.id(), later.id()), service.mine("c1", "upcoming").stream().map(AppointmentResponse::id).toList());
        assertEquals(List.of(cancelled.id()), service.mine("c1", "previous").stream().map(AppointmentResponse::id).toList());
        assertEquals(3, service.mine("c1", null).size());
        assertEquals(0, service.mine("c2", "all").size());
        assertThrows(FieldValidationException.class, () -> service.mine("c1", "soonish"));
    }

    // ---------- customer cancellations ----------

    @Test
    void cancellingFreesTheSlotForSomeoneElse() {
        Slot s = tomorrowAt(dentist, 9, 0);
        AppointmentResponse a = book("c1", s);
        loginAs("c2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> service.cancelOwn("c2", a.id(), null));
        loginAs("c1", Role.CUSTOMER);
        AppointmentResponse done = service.cancelOwn("c1", a.id(), "Feeling better");
        assertEquals("CANCELLED", done.status());
        assertEquals("c1", done.cancelledBy());
        assertThrows(InvalidStatusException.class, () -> service.cancelOwn("c1", a.id(), null));
        assertEquals("PENDING", book("c2", s).status());
    }

    @Test
    void cancellingNeedsTwoHoursNotice() {
        Slot soon = slot(dentist, LocalDateTime.now().plusMinutes(90), 30);
        AppointmentResponse a = book("c1", soon);
        assertFalse(a.canCancel());
        loginAs("c1", Role.CUSTOMER);
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> service.cancelOwn("c1", a.id(), null));
        assertTrue(e.getMessage().contains("2 hours"));
        Slot enough = slot(dentist, LocalDateTime.now().plusHours(3), 30);
        AppointmentResponse b = book("c2", enough);
        loginAs("c2", Role.CUSTOMER);
        assertEquals("CANCELLED", service.cancelOwn("c2", b.id(), null).status());
    }

    // ---------- the provider's decisions ----------

    @Test
    void onlyTheProviderOfTheSlotConfirms() {
        AppointmentResponse a = book("c1", tomorrowAt(dentist, 9, 0));
        loginAs("prov2", Role.SERVICE_PROVIDER);
        assertThrows(ForbiddenActionException.class, () -> service.confirm("prov2", a.id()));
        loginAs("c1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> service.confirm("c1", a.id()));
        AppointmentResponse done = confirm(dentist, a);
        assertEquals("CONFIRMED", done.status());
        assertEquals("prov1", done.confirmedBy());
        assertFalse(done.canConfirm());
        assertThrows(InvalidStatusException.class, () -> confirm(dentist, a));
    }

    @Test
    void anAppointmentWhoseTimeHasPassedCannotBeConfirmed() {
        AppointmentResponse old = bookDirect("c2", slot(dentist, LocalDateTime.now().minusHours(3), 30));
        assertThrows(BusinessRuleException.class, () -> confirm(dentist, old));
    }

    /** An appointment that was made while its slot was still in the future. */
    AppointmentResponse bookDirect(String customer, Slot slot) {
        var saved = appointments.save(new com.hackathon.appointment.entity.Appointment(slot, users.findByUsername(customer).orElseThrow(), null));
        return AppointmentResponse.of(appointments.findDetailedById(saved.getId()).orElseThrow(), LocalDateTime.now());
    }

    @Test
    void theProviderMustGiveAReasonAndTheSlotIsFreedAgain() {
        Slot s = tomorrowAt(dentist, 9, 0);
        AppointmentResponse a = confirm(dentist, book("c1", s));
        loginAs("prov1", Role.SERVICE_PROVIDER);
        FieldValidationException e = assertThrows(FieldValidationException.class, () -> service.cancelAsProvider("prov1", a.id(), "   "));
        assertEquals("reason", e.getField());
        loginAs("prov2", Role.SERVICE_PROVIDER);
        assertThrows(ForbiddenActionException.class, () -> service.cancelAsProvider("prov2", a.id(), "Mine now"));
        loginAs("prov1", Role.SERVICE_PROVIDER);
        AppointmentResponse cancelled = service.cancelAsProvider("prov1", a.id(), "Emergency");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("Emergency", cancelled.cancelReason());
        assertEquals("prov1", cancelled.cancelledBy());
        assertThrows(InvalidStatusException.class, () -> service.cancelAsProvider("prov1", a.id(), "again"));
        assertEquals("PENDING", book("c2", s).status());
    }

    @Test
    void aPastAppointmentCannotBeCancelledByTheProvider() {
        Slot past = slot(dentist, LocalDateTime.now().minusHours(3), 30);
        AppointmentResponse a = bookDirect("c1", past);
        loginAs("prov1", Role.SERVICE_PROVIDER);
        assertThrows(BusinessRuleException.class, () -> service.cancelAsProvider("prov1", a.id(), "Too late"));
    }

    @Test
    void theProviderListFiltersAndPutsPendingFirst() {
        AppointmentResponse first = book("c1", tomorrowAt(dentist, 9, 0));
        AppointmentResponse second = book("c2", tomorrowAt(dentist, 9, 30));
        AppointmentResponse third = book("c3", tomorrowAt(dentist, 10, 0));
        confirm(dentist, first);
        book("c4", tomorrowAt(physio, 9, 0));
        loginAs("prov1", Role.SERVICE_PROVIDER);
        List<AppointmentResponse> all = service.appointments("prov1", null, null, null);
        assertEquals(3, all.size());
        assertEquals(List.of(second.id(), third.id(), first.id()), all.stream().map(AppointmentResponse::id).toList());
        assertEquals(1, service.appointments("prov1", AppointmentStatus.CONFIRMED, null, null).size());
        assertEquals(3, service.appointments("prov1", null, tomorrow, null).size());
        assertEquals(0, service.appointments("prov1", null, tomorrow.plusDays(1), null).size());
        assertEquals(List.of(second.id()), service.appointments("prov1", null, null, "customer 2").stream().map(AppointmentResponse::id).toList());
        assertEquals(3, service.appointments("prov1", null, null, "check-up").size());
    }

    @Test
    void theProfileIsCreatedWhenMissingAndCanBeEdited() {
        users.save(new User("prov3", "x", "Newcomer", Role.SERVICE_PROVIDER));
        loginAs("prov3", Role.SERVICE_PROVIDER);
        assertEquals("Service provider", service.profile("prov3").title());
        ProviderResponse updated = service.updateProfile("prov3", new ProfileBody(" Yoga teacher ", "Calm", "  "));
        assertEquals("Yoga teacher", updated.title());
        assertNull(updated.location());
    }

    @Test
    void theDashboardSummarisesTheWeek() {
        Slot a = tomorrowAt(dentist, 9, 0);
        Slot b = tomorrowAt(dentist, 9, 30);
        tomorrowAt(dentist, 10, 0);
        AppointmentResponse pending = book("c1", a);
        AppointmentResponse confirmed = confirm(dentist, book("c2", b));
        loginAs("prov1", Role.SERVICE_PROVIDER);
        ProviderDashboard d = service.dashboard("prov1");
        assertEquals(0, d.todayAppointments());
        assertEquals(1, d.pending());
        assertEquals(1, d.upcomingConfirmed());
        assertEquals(1, d.freeSlotsWeek());
        assertEquals(2, d.bookedSlotsWeek());
        assertEquals(67, d.utilisationPercent());
        assertEquals(7, d.days().size());
        assertEquals(3, d.days().get(1).slots());
        assertEquals(2, d.days().get(1).booked());
        assertEquals(List.of(pending.id(), confirmed.id()), d.next().stream().map(AppointmentResponse::id).toList());
    }

    // ---------- concurrency ----------

    private static long winners(List<Future<Boolean>> results) throws Exception {
        long wins = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                wins++;
            }
        }
        return wins;
    }

    @Test
    void fiveCustomersRacingForOneSlotProduceOneAppointment() throws Exception {
        Slot s = tomorrowAt(dentist, 9, 0);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.rangeClosed(1, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("c" + i, Role.CUSTOMER);
                try {
                    service.book("c" + i, new AppointmentRequest(s.getId(), null));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, appointments.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void oneCustomerClickingTwoOverlappingSlotsGetsOnlyOne() throws Exception {
        Slot a = tomorrowAt(dentist, 9, 0);
        Slot b = slot(physio, tomorrow.atTime(9, 10), 30);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(a, b).stream().<Callable<Boolean>>map(s -> () -> {
                loginAs("c1", Role.CUSTOMER);
                try {
                    service.book("c1", new AppointmentRequest(s.getId(), null));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, appointments.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoRequestsCreatingTheSameWindowMakeOneSet() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 2).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("prov1", Role.SERVICE_PROVIDER);
                try {
                    service.createBulk("prov1", new BulkSlotBody(tomorrow, LocalTime.of(9, 0), LocalTime.of(11, 0), 30));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(4, slots.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aCustomerCancellingWhileTheProviderConfirmsEndsConsistently() throws Exception {
        AppointmentResponse a = book("c1", tomorrowAt(dentist, 9, 0));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> cancel = () -> {
                loginAs("c1", Role.CUSTOMER);
                try {
                    service.cancelOwn("c1", a.id(), "Changed my mind");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Callable<Boolean> approve = () -> {
                loginAs("prov1", Role.SERVICE_PROVIDER);
                try {
                    service.confirm("prov1", a.id());
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            long wins = winners(pool.invokeAll(List.of(cancel, approve)));
            // confirm-then-cancel is allowed (both succeed); cancel-then-confirm is refused. Either way the appointment ends cancelled.
            assertTrue(wins >= 1);
            assertEquals(AppointmentStatus.CANCELLED, appointments.findById(a.id()).orElseThrow().getStatus());
        } finally {
            pool.shutdownNow();
        }
    }
}
