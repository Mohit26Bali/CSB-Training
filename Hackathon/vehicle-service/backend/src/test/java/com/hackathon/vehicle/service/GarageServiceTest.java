package com.hackathon.vehicle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.hackathon.vehicle.dto.BookingRequest;
import com.hackathon.vehicle.dto.BookingResponse;
import com.hackathon.vehicle.dto.CompleteBody;
import com.hackathon.vehicle.dto.DayLoad;
import com.hackathon.vehicle.dto.ServiceDashboard;
import com.hackathon.vehicle.dto.ServiceTypeBody;
import com.hackathon.vehicle.dto.ServiceTypeResponse;
import com.hackathon.vehicle.dto.VehicleBody;
import com.hackathon.vehicle.dto.VehicleResponse;
import com.hackathon.vehicle.entity.BookingStatus;
import com.hackathon.vehicle.entity.Center;
import com.hackathon.vehicle.entity.Role;
import com.hackathon.vehicle.entity.ServiceType;
import com.hackathon.vehicle.entity.User;
import com.hackathon.vehicle.entity.Vehicle;
import com.hackathon.vehicle.entity.VehicleType;
import com.hackathon.vehicle.exception.BusinessRuleException;
import com.hackathon.vehicle.exception.ConflictException;
import com.hackathon.vehicle.exception.FieldValidationException;
import com.hackathon.vehicle.exception.ForbiddenActionException;
import com.hackathon.vehicle.exception.InvalidStatusException;
import com.hackathon.vehicle.repo.CenterRepository;
import com.hackathon.vehicle.repo.ServiceBookingRepository;
import com.hackathon.vehicle.repo.ServiceTypeRepository;
import com.hackathon.vehicle.repo.UserRepository;
import com.hackathon.vehicle.repo.VehicleRepository;

@SpringBootTest
@ActiveProfiles("test")
class GarageServiceTest {

    @Autowired GarageService garage;
    @Autowired VehicleRepository vehicles;
    @Autowired ServiceTypeRepository services;
    @Autowired ServiceBookingRepository bookings;
    @Autowired CenterRepository centers;
    @Autowired UserRepository users;

    ServiceType basic;
    ServiceType full;
    ServiceType retired;
    Vehicle car1;
    Vehicle bike1;
    Vehicle car2;
    Vehicle suv3;
    Vehicle car4;
    LocalDate today;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        vehicles.deleteAll();
        services.deleteAll();
        centers.deleteAll();
        users.deleteAll();
        today = LocalDate.now();
        users.save(new User("admin", "x", "Admin", Role.SERVICE_ADMIN));
        User c1 = users.save(new User("c1", "x", "Customer One", Role.CUSTOMER));
        User c2 = users.save(new User("c2", "x", "Customer Two", Role.CUSTOMER));
        User c3 = users.save(new User("c3", "x", "Customer Three", Role.CUSTOMER));
        User c4 = users.save(new User("c4", "x", "Customer Four", Role.CUSTOMER));
        centers.save(new Center(2));
        basic = services.save(new ServiceType("Basic service", "Oil and checks", 1, new BigDecimal("2000.00"), true));
        full = services.save(new ServiceType("Full service", "Everything", 2, new BigDecimal("6000.00"), true));
        retired = services.save(new ServiceType("Old service", null, 1, new BigDecimal("1000.00"), false));
        car1 = vehicles.save(new Vehicle(c1, "MH12AB1111", VehicleType.CAR, "Maruti", "Swift", 2019));
        bike1 = vehicles.save(new Vehicle(c1, "MH12AB2222", VehicleType.BIKE, "Bajaj", "Pulsar", 2020));
        car2 = vehicles.save(new Vehicle(c2, "MH12AB3333", VehicleType.CAR, "Honda", "City", 2018));
        suv3 = vehicles.save(new Vehicle(c3, "MH12AB4444", VehicleType.SUV, "Tata", "Nexon", 2022));
        car4 = vehicles.save(new Vehicle(c4, "MH12AB5555", VehicleType.CAR, "Hyundai", "i20", 2021));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    LocalDate day(int offset) {
        return today.plusDays(offset);
    }

    String owner(Vehicle v) {
        return v.getOwner().getUsername();
    }

    BookingResponse book(Vehicle v, ServiceType type, int offset) {
        loginAs(owner(v), Role.CUSTOMER);
        return garage.book(owner(v), new BookingRequest(v.getId(), type.getId(), day(offset), null));
    }

    BookingResponse start(BookingResponse b) {
        loginAs("admin", Role.SERVICE_ADMIN);
        return garage.start("admin", b.id());
    }

    BookingResponse complete(BookingResponse b, CompleteBody body) {
        loginAs("admin", Role.SERVICE_ADMIN);
        return garage.complete("admin", b.id(), body);
    }

    // ---------- vehicles ----------

    @Test
    void registeringStoresTheNumberTidyAndRejectsDuplicatesInAnyFormat() {
        loginAs("c1", Role.CUSTOMER);
        VehicleResponse v = garage.registerVehicle("c1", new VehicleBody("mh 12-xy 9999", VehicleType.SUV, " Mahindra ", "XUV700", 2023));
        assertEquals("MH12XY9999", v.number());
        assertEquals("Mahindra", v.make());
        FieldValidationException e = assertThrows(FieldValidationException.class,
                () -> garage.registerVehicle("c2", new VehicleBody("MH12-XY-9999", VehicleType.CAR, "Tata", "Nano", 2015)));
        assertEquals("number", e.getField());
        assertThrows(FieldValidationException.class, () -> garage.registerVehicle("c1", new VehicleBody("MH12AB1111", VehicleType.CAR, "X", "Y", 2020)));
    }

    @Test
    void customersSeeOnlyTheirOwnVehicles() {
        loginAs("c1", Role.CUSTOMER);
        assertEquals(2, garage.myVehicles("c1").size());
        loginAs("c2", Role.CUSTOMER);
        assertEquals(List.of("MH12AB3333"), garage.myVehicles("c2").stream().map(VehicleResponse::number).toList());
    }

    @Test
    void aVehicleWithBookingsCannotBeRemovedAndOnlyTheOwnerMayRemoveOne() {
        book(car1, basic, 3);
        loginAs("c1", Role.CUSTOMER);
        assertThrows(BusinessRuleException.class, () -> garage.deleteVehicle("c1", car1.getId()));
        assertThrows(ForbiddenActionException.class, () -> garage.deleteVehicle("c2", bike1.getId()));
        garage.deleteVehicle("c1", bike1.getId());
        assertEquals(1, garage.myVehicles("c1").size());
    }

    // ---------- catalogue ----------

    @Test
    void theCatalogueHidesRetiredServicesAndPricesForTheVehicleType() {
        loginAs("c1", Role.CUSTOMER);
        List<ServiceTypeResponse> bike = garage.catalogue("bike");
        assertEquals(2, bike.size());
        assertEquals(new BigDecimal("1200.00"), bike.stream().filter(s -> s.name().equals("Basic service")).findFirst().orElseThrow().estimatedCost());
        assertEquals(new BigDecimal("7800.00"), garage.catalogue("SUV").stream().filter(s -> s.name().equals("Full service")).findFirst().orElseThrow().estimatedCost());
        assertEquals(new BigDecimal("2000.00"), garage.catalogue(null).stream().filter(s -> s.name().equals("Basic service")).findFirst().orElseThrow().estimatedCost());
        assertThrows(FieldValidationException.class, () -> garage.catalogue("boat"));
    }

    // ---------- booking rules ----------

    @Test
    void bookingCreatesABookedServiceWithEndDateAndEstimate() {
        BookingResponse b = book(suv3, full, 4);
        assertEquals("BOOKED", b.status());
        assertEquals(day(4), b.startDate());
        assertEquals(day(5), b.endDate());
        assertEquals(new BigDecimal("7800.00"), b.estimatedCost());
        assertTrue(b.canCancel());
        assertFalse(b.canStart());
        assertTrue(b.number().startsWith("VS-"));
    }

    @Test
    void theServiceDateMustBeTodayOrLaterAndWithinNinetyDays() {
        loginAs("c1", Role.CUSTOMER);
        FieldValidationException past = assertThrows(FieldValidationException.class, () -> book(car1, basic, -1));
        assertEquals("serviceDate", past.getField());
        assertThrows(FieldValidationException.class, () -> book(car1, basic, 91));
        assertEquals(day(0), book(car1, basic, 0).startDate());
        assertEquals(day(90), book(bike1, basic, 90).startDate());
    }

    @Test
    void youCannotBookSomeoneElsesVehicleOrARetiredService() {
        loginAs("c2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> garage.book("c2", new BookingRequest(car1.getId(), basic.getId(), day(3), null)));
        assertThrows(BusinessRuleException.class, () -> garage.book("c1", new BookingRequest(car1.getId(), retired.getId(), day(3), null)));
        assertThrows(com.hackathon.vehicle.exception.ResourceNotFoundException.class, () -> garage.book("c1", new BookingRequest(999999L, basic.getId(), day(3), null)));
    }

    @Test
    void aVehicleCannotHaveOverlappingBookings() {
        book(car1, full, 5); // days 5 and 6
        assertThrows(ConflictException.class, () -> book(car1, basic, 6));
        assertThrows(ConflictException.class, () -> book(car1, full, 4));
        assertEquals(day(7), book(car1, basic, 7).startDate());
        assertEquals(day(6), book(bike1, basic, 6).startDate()); // another vehicle is fine
    }

    @Test
    void whenEveryBayIsTakenTheDayIsFull() {
        book(car1, basic, 5);
        book(car2, basic, 5);
        ConflictException e = assertThrows(ConflictException.class, () -> book(suv3, basic, 5));
        assertTrue(e.getMessage().contains("fully booked"));
        assertThrows(ConflictException.class, () -> book(suv3, full, 4)); // the second day of a two-day service hits the full day
        assertEquals(day(6), book(suv3, basic, 6).startDate());
    }

    @Test
    void aCancelledBookingGivesTheBayBack() {
        BookingResponse a = book(car1, basic, 5);
        book(car2, basic, 5);
        loginAs("c1", Role.CUSTOMER);
        garage.cancelOwn("c1", a.id(), "Plans changed");
        assertEquals(day(5), book(suv3, basic, 5).startDate());
        assertThrows(ConflictException.class, () -> book(car1, basic, 5)); // the freed bay is taken again
    }

    @Test
    void availabilityShowsBookedAndFreeBaysPerDay() {
        book(car1, full, 1);
        book(car2, basic, 2);
        loginAs("c3", Role.CUSTOMER);
        List<DayLoad> days = garage.availability(day(0), 4);
        assertEquals(List.of(0, 1, 2, 0), days.stream().map(DayLoad::booked).toList());
        assertEquals(List.of(2, 1, 0, 2), days.stream().map(DayLoad::free).toList());
        assertEquals(14, garage.availability(null, null).size());
        assertThrows(FieldValidationException.class, () -> garage.availability(day(-1), 3));
        assertThrows(FieldValidationException.class, () -> garage.availability(day(0), 61));
    }

    // ---------- my bookings ----------

    @Test
    void myBookingsAreSplitIntoUpcomingAndPrevious() {
        BookingResponse soon = book(car1, basic, 3);
        BookingResponse later = book(bike1, basic, 10);
        BookingResponse cancelled = book(car1, basic, 20);
        loginAs("c1", Role.CUSTOMER);
        garage.cancelOwn("c1", cancelled.id(), null);
        assertEquals(List.of(soon.id(), later.id()), garage.mine("c1", "upcoming").stream().map(BookingResponse::id).toList());
        assertEquals(List.of(cancelled.id()), garage.mine("c1", "previous").stream().map(BookingResponse::id).toList());
        assertEquals(3, garage.mine("c1", null).size());
        assertEquals(0, garage.mine("c2", "all").size());
        assertThrows(FieldValidationException.class, () -> garage.mine("c1", "soonish"));
    }

    @Test
    void onlyTheOwnerCancelsAndOnlyWhileBooked() {
        BookingResponse b = book(car1, basic, 0);
        loginAs("c2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> garage.cancelOwn("c2", b.id(), null));
        start(b);
        loginAs("c1", Role.CUSTOMER);
        assertThrows(InvalidStatusException.class, () -> garage.cancelOwn("c1", b.id(), null));
        BookingResponse other = book(bike1, basic, 4);
        BookingResponse done = garage.cancelOwn("c1", other.id(), "Not needed");
        assertEquals("CANCELLED", done.status());
        assertEquals("c1", done.cancelledBy());
        assertThrows(InvalidStatusException.class, () -> garage.cancelOwn("c1", other.id(), null));
    }

    // ---------- the garage's work ----------

    @Test
    void statusMovesFromBookedToInServiceToCompleted() {
        BookingResponse b = book(car1, basic, 0);
        assertTrue(b.canStart());
        BookingResponse started = start(b);
        assertEquals("IN_SERVICE", started.status());
        assertEquals("admin", started.startedBy());
        assertFalse(started.canCancel());
        assertThrows(InvalidStatusException.class, () -> start(b));
        BookingResponse done = complete(b, new CompleteBody(new BigDecimal("2150"), "Changed oil"));
        assertEquals("COMPLETED", done.status());
        assertEquals(new BigDecimal("2150.00"), done.finalCost());
        assertEquals("Changed oil", done.workNotes());
        assertThrows(InvalidStatusException.class, () -> complete(b, new CompleteBody(null, null)));
    }

    @Test
    void aServiceCannotStartBeforeItsDateOrBeCompletedBeforeItStarts() {
        BookingResponse future = book(car1, basic, 3);
        assertThrows(BusinessRuleException.class, () -> start(future));
        assertThrows(InvalidStatusException.class, () -> complete(future, new CompleteBody(null, null)));
    }

    @Test
    void completingWithoutACostUsesTheEstimate() {
        BookingResponse b = start(book(suv3, basic, 0));
        assertEquals(new BigDecimal("2600.00"), complete(b, new CompleteBody(null, null)).finalCost());
    }

    @Test
    void theGarageMustGiveAReasonToCancel() {
        BookingResponse b = book(car1, basic, 3);
        loginAs("admin", Role.SERVICE_ADMIN);
        FieldValidationException e = assertThrows(FieldValidationException.class, () -> garage.cancelAsAdmin("admin", b.id(), "  "));
        assertEquals("reason", e.getField());
        BookingResponse cancelled = garage.cancelAsAdmin("admin", b.id(), "Mechanic unavailable");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("Mechanic unavailable", cancelled.cancelReason());
        assertThrows(InvalidStatusException.class, () -> garage.cancelAsAdmin("admin", b.id(), "again"));
    }

    @Test
    void rolesAreEnforcedOnTheServiceMethods() {
        BookingResponse b = book(car1, basic, 0);
        loginAs("c1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> garage.start("c1", b.id()));
        assertThrows(AccessDeniedException.class, () -> garage.allBookings(null, null, null));
        assertThrows(AccessDeniedException.class, () -> garage.dashboard());
        assertThrows(AccessDeniedException.class, () -> garage.searchVehicles("x"));
        loginAs("admin", Role.SERVICE_ADMIN);
        assertThrows(AccessDeniedException.class, () -> garage.book("admin", new BookingRequest(car1.getId(), basic.getId(), day(2), null)));
        assertThrows(AccessDeniedException.class, () -> garage.mine("admin", null));
    }

    @Test
    void historyHoldsFinishedServicesOfOneVehicleForItsOwnerAndTheGarage() {
        complete(start(book(car1, basic, 0)), new CompleteBody(null, null));
        book(car1, basic, 5);
        loginAs("c1", Role.CUSTOMER);
        List<BookingResponse> history = garage.history("c1", car1.getId());
        assertEquals(1, history.size());
        assertEquals("COMPLETED", history.get(0).status());
        assertThrows(ForbiddenActionException.class, () -> garage.history("c2", car1.getId()));
        loginAs("admin", Role.SERVICE_ADMIN);
        assertEquals(1, garage.vehicleHistory(car1.getId()).size());
    }

    @Test
    void theGarageSearchesVehiclesByNumberOwnerOrModel() {
        book(car1, basic, 4);
        loginAs("admin", Role.SERVICE_ADMIN);
        assertEquals(5, garage.searchVehicles(null).size());
        assertEquals(List.of("MH12AB3333"), garage.searchVehicles("mh12 ab-3333").stream().map(VehicleResponse::number).toList());
        assertEquals(2, garage.searchVehicles("customer one").size());
        assertEquals(List.of("MH12AB4444"), garage.searchVehicles("nexon").stream().map(VehicleResponse::number).toList());
        VehicleResponse swift = garage.searchVehicles("swift").get(0);
        assertEquals(day(4), swift.nextService());
        assertEquals(0, swift.completedServices());
    }

    @Test
    void theScheduleFiltersByStatusDateAndText() {
        BookingResponse a = book(car1, basic, 2);
        book(car2, full, 2);
        BookingResponse c = book(suv3, basic, 6);
        loginAs("admin", Role.SERVICE_ADMIN);
        garage.cancelAsAdmin("admin", c.id(), "No parts");
        assertEquals(3, garage.allBookings(null, null, null).size());
        assertEquals(2, garage.allBookings(BookingStatus.BOOKED, null, null).size());
        assertEquals(2, garage.allBookings(null, day(2), null).size());
        assertEquals(1, garage.allBookings(null, day(3), null).size()); // only the two-day full service
        assertEquals(List.of(a.id()), garage.allBookings(null, null, "mh12ab1111").stream().map(BookingResponse::id).toList());
        assertEquals(1, garage.allBookings(null, null, "nexon").size());
        assertEquals("BOOKED", garage.allBookings(null, null, null).get(0).status()); // cancelled ones are listed last
    }

    @Test
    void theCatalogueIsManagedByTheGarage() {
        loginAs("admin", Role.SERVICE_ADMIN);
        ServiceTypeResponse created = garage.createService(new ServiceTypeBody(" Tyre rotation ", "Rotate all tyres", 1, new BigDecimal("900"), null));
        assertEquals("Tyre rotation", created.name());
        assertTrue(created.active());
        FieldValidationException dup = assertThrows(FieldValidationException.class,
                () -> garage.createService(new ServiceTypeBody("tyre ROTATION", null, 1, new BigDecimal("900"), true)));
        assertEquals("name", dup.getField());
        ServiceTypeResponse changed = garage.updateService(created.id(), new ServiceTypeBody("Tyre rotation", "New text", 2, new BigDecimal("1100"), false));
        assertFalse(changed.active());
        assertEquals(2, changed.durationDays());
        assertThrows(FieldValidationException.class, () -> garage.updateService(created.id(), new ServiceTypeBody("Basic service", null, 1, new BigDecimal("900"), true)));
        assertEquals(4, garage.allServices().size());
        loginAs("c1", Role.CUSTOMER);
        assertEquals(2, garage.catalogue(null).size());
    }

    @Test
    void theNumberOfBaysCannotDropBelowWhatIsAlreadyBooked() {
        book(car1, basic, 5);
        book(car2, basic, 5);
        loginAs("admin", Role.SERVICE_ADMIN);
        FieldValidationException e = assertThrows(FieldValidationException.class, () -> garage.setBays(1));
        assertEquals("bays", e.getField());
        assertEquals(4, garage.setBays(4));
        assertEquals(4, garage.baysNow());
        assertEquals(day(5), book(suv3, basic, 5).startDate());
    }

    @Test
    void theDashboardSummarisesTheWorkshop() {
        loginAs("admin", Role.SERVICE_ADMIN);
        garage.setBays(4);
        start(book(car1, basic, 0));
        book(car2, basic, 0);
        BookingResponse finished = start(book(suv3, basic, 0));
        complete(finished, new CompleteBody(new BigDecimal("3000"), null));
        book(car4, full, 3);
        loginAs("admin", Role.SERVICE_ADMIN);
        ServiceDashboard d = garage.dashboard();
        assertEquals(4, d.bays());
        assertEquals(2, d.baysUsedToday());
        assertEquals(1, d.arrivalsToday());
        assertEquals(1, d.inServiceNow());
        assertEquals(2, d.upcomingBooked());
        assertEquals(1, d.completedThisMonth());
        assertEquals(new BigDecimal("3000.00"), d.revenueThisMonth());
        assertEquals("Basic service", d.topServices().get(0).service());
        assertEquals(3L, d.topServices().get(0).count());
        assertEquals(14, d.load().size());
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
    void aDoubleClickedBookingCreatesOneBooking() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("c1", Role.CUSTOMER);
                try {
                    garage.book("c1", new BookingRequest(car1.getId(), basic.getId(), day(5), null));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, bookings.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theLastBaysAreNeverOversold() throws Exception {
        List<Vehicle> rivals = List.of(car1, car2, suv3, car4);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = rivals.stream().<Callable<Boolean>>map(v -> () -> {
                loginAs(owner(v), Role.CUSTOMER);
                try {
                    garage.book(owner(v), new BookingRequest(v.getId(), basic.getId(), day(7), null));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(2, winners(pool.invokeAll(tasks)));
            assertEquals(2, bookings.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aCustomerCancellingWhileTheGarageStartsEndsConsistently() throws Exception {
        BookingResponse b = book(car1, basic, 0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> cancel = () -> {
                loginAs("c1", Role.CUSTOMER);
                try {
                    garage.cancelOwn("c1", b.id(), "Changed my mind");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Callable<Boolean> begin = () -> {
                loginAs("admin", Role.SERVICE_ADMIN);
                try {
                    garage.start("admin", b.id());
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            assertEquals(1, winners(pool.invokeAll(List.of(cancel, begin))));
            BookingStatus status = bookings.findById(b.id()).orElseThrow().getStatus();
            assertTrue(status == BookingStatus.CANCELLED || status == BookingStatus.IN_SERVICE);
        } finally {
            pool.shutdownNow();
        }
    }
}
