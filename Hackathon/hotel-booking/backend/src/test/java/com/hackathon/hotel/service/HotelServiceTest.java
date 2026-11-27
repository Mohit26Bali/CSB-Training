package com.hackathon.hotel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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

import com.hackathon.hotel.dto.BookingRequest;
import com.hackathon.hotel.dto.BookingResponse;
import com.hackathon.hotel.dto.CalendarResponse;
import com.hackathon.hotel.dto.HotelDashboard;
import com.hackathon.hotel.dto.RoomBody;
import com.hackathon.hotel.dto.RoomResponse;
import com.hackathon.hotel.entity.Booking;
import com.hackathon.hotel.entity.BookingStatus;
import com.hackathon.hotel.entity.Role;
import com.hackathon.hotel.entity.Room;
import com.hackathon.hotel.entity.RoomType;
import com.hackathon.hotel.entity.User;
import com.hackathon.hotel.exception.BusinessRuleException;
import com.hackathon.hotel.exception.ConflictException;
import com.hackathon.hotel.exception.FieldValidationException;
import com.hackathon.hotel.exception.ForbiddenActionException;
import com.hackathon.hotel.exception.InvalidStatusException;
import com.hackathon.hotel.repo.BookingRepository;
import com.hackathon.hotel.repo.RoomRepository;
import com.hackathon.hotel.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class HotelServiceTest {

    @Autowired HotelService hotel;
    @Autowired RoomRepository rooms;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;

    Room standard;
    Room deluxe;
    Room suite;
    Room closed;
    LocalDate today;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        rooms.deleteAll();
        users.deleteAll();
        today = LocalDate.now();
        users.save(new User("admin", "x", "Admin", Role.HOTEL_ADMIN));
        users.save(new User("admin2", "x", "Admin Two", Role.HOTEL_ADMIN));
        for (int i = 1; i <= 5; i++) {
            users.save(new User("g" + i, "x", "Guest " + i, Role.CUSTOMER));
        }
        standard = rooms.save(new Room("101", RoomType.STANDARD, 1, new BigDecimal("3000.00"), 2, "Cosy", "Wi-Fi, TV"));
        deluxe = rooms.save(new Room("201", RoomType.DELUXE, 2, new BigDecimal("5000.00"), 3, "Roomy", "Wi-Fi, Minibar"));
        suite = rooms.save(new Room("301", RoomType.SUITE, 3, new BigDecimal("10000.00"), 4, "Grand", null));
        closed = new Room("102", RoomType.STANDARD, 1, new BigDecimal("3000.00"), 2, null, null);
        closed.update("102", RoomType.STANDARD, 1, new BigDecimal("3000.00"), 2, null, null, false);
        closed = rooms.save(closed);
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

    BookingResponse book(String guest, Room room, int inOffset, int outOffset) {
        loginAs(guest, Role.CUSTOMER);
        return hotel.book(guest, new BookingRequest(room.getId(), day(inOffset), day(outOffset), 2, null));
    }

    BookingResponse confirm(BookingResponse b) {
        loginAs("admin", Role.HOTEL_ADMIN);
        return hotel.confirm(b.id(), "admin");
    }

    /** Puts a confirmed booking straight into the database, as if it had been made earlier. */
    void confirmedStay(Room room, String guest, int inOffset, int outOffset) {
        Booking b = new Booking(room, users.findByUsername(guest).orElseThrow(), day(inOffset), day(outOffset), 2,
                room.getPricePerNight().multiply(BigDecimal.valueOf(outOffset - inOffset)), outOffset - inOffset, null);
        b.confirm("admin");
        bookings.save(b);
    }

    // ---------- searching ----------

    @Test
    void availableRoomsAreActiveFitTheGuestsAndAreCostedForTheStay() {
        loginAs("g1", Role.CUSTOMER);
        List<RoomResponse> all = hotel.available(day(5), day(8), null, null);
        assertEquals(List.of("101", "201", "301"), all.stream().map(RoomResponse::number).toList()); // the closed room is hidden
        assertEquals(3, all.get(0).nights());
        assertEquals(0, new BigDecimal("9000.00").compareTo(all.get(0).totalPrice()));
        assertEquals(List.of("201"), hotel.available(day(5), day(8), "deluxe", null).stream().map(RoomResponse::number).toList());
        assertEquals(List.of("201", "301"), hotel.available(day(5), day(8), null, 3).stream().map(RoomResponse::number).toList());
        assertEquals(List.of(), hotel.available(day(5), day(8), null, 9));
        assertEquals("type", assertThrows(FieldValidationException.class, () -> hotel.available(day(5), day(8), "palace", null)).getField());
    }

    @Test
    void searchValidatesTheDates() {
        assertEquals("checkIn", assertThrows(FieldValidationException.class, () -> hotel.available(day(-1), day(2), null, null)).getField());
        assertEquals("checkOut", assertThrows(FieldValidationException.class, () -> hotel.available(day(3), day(3), null, null)).getField());
        assertEquals("checkOut", assertThrows(FieldValidationException.class, () -> hotel.available(day(3), day(2), null, null)).getField());
        assertEquals("checkOut", assertThrows(FieldValidationException.class, () -> hotel.available(day(3), day(40), null, null)).getField());
        assertEquals(3, hotel.available(today, day(1), null, null).size()); // checking in today is fine
    }

    @Test
    void confirmedStaysHideARoomButBackToBackStaysAreAllowed() {
        confirmedStay(standard, "g2", 5, 8);
        assertEquals(List.of("201", "301"), hotel.available(day(6), day(7), null, null).stream().map(RoomResponse::number).toList());
        assertEquals(3, hotel.available(day(8), day(10), null, null).size()); // the check-out day is free for the next guest
        assertEquals(3, hotel.available(day(2), day(5), null, null).size());
        assertEquals(2, hotel.available(day(2), day(6), null, null).size()); // one night overlaps
    }

    // ---------- booking ----------

    @Test
    void aGuestBooksAndGetsTheTotalAndAReference() {
        BookingResponse b = book("g1", deluxe, 5, 8);
        assertEquals("PENDING", b.status());
        assertEquals(3, b.nights());
        assertEquals(0, new BigDecimal("15000.00").compareTo(b.totalPrice()));
        assertTrue(b.number().startsWith("HB-"));
        assertEquals("UPCOMING", b.phase());
        assertTrue(b.canCancel());
    }

    @Test
    void bookingRulesAreEnforced() {
        loginAs("g1", Role.CUSTOMER);
        assertEquals("checkIn", assertThrows(FieldValidationException.class, () -> hotel.book("g1", new BookingRequest(standard.getId(), day(-2), day(1), 1, null))).getField());
        assertEquals("checkOut", assertThrows(FieldValidationException.class, () -> hotel.book("g1", new BookingRequest(standard.getId(), day(3), day(3), 1, null))).getField());
        assertEquals("guests", assertThrows(FieldValidationException.class, () -> hotel.book("g1", new BookingRequest(standard.getId(), day(3), day(4), 3, null))).getField());
        assertEquals("roomId", assertThrows(FieldValidationException.class, () -> hotel.book("g1", new BookingRequest(9999L, day(3), day(4), 1, null))).getField());
        assertThrows(BusinessRuleException.class, () -> hotel.book("g1", new BookingRequest(closed.getId(), day(3), day(4), 1, null)));
    }

    @Test
    void aConfirmedStayBlocksOverlappingBookingsButPendingOnesDoNot() {
        confirmedStay(standard, "g2", 10, 13);
        ConflictException ex = assertThrows(ConflictException.class, () -> book("g1", standard, 12, 15));
        assertTrue(ex.getMessage().contains("not available"));
        assertEquals("PENDING", book("g1", standard, 13, 15).status()); // starts on the check-out day
        assertEquals("PENDING", book("g3", deluxe, 5, 7).status());
        assertEquals("PENDING", book("g4", deluxe, 6, 8).status()); // two guests may both ask for the same dates
    }

    @Test
    void aGuestCannotBookTheSameRoomTwiceForOverlappingDates() {
        book("g1", standard, 5, 8);
        assertThrows(ConflictException.class, () -> book("g1", standard, 7, 9));
        assertEquals("PENDING", book("g1", standard, 8, 9).status());
    }

    @Test
    void guestsSeeOnlyTheirOwnBookingsWithTheirPhase() {
        book("g1", standard, 5, 8);
        BookingResponse stay = book("g1", deluxe, 5, 8);
        book("g2", suite, 5, 8);
        confirm(stay);
        loginAs("g1", Role.CUSTOMER);
        assertEquals(2, hotel.mine("g1", null).size());
        assertEquals(1, hotel.mine("g1", BookingStatus.CONFIRMED).size());
        loginAs("g2", Role.CUSTOMER);
        assertEquals(1, hotel.mine("g2", null).size());
    }

    // ---------- confirming ----------

    @Test
    void confirmingReleasesOtherPendingRequestsForTheSameDates() {
        BookingResponse first = book("g1", deluxe, 5, 8);
        BookingResponse rival = book("g2", deluxe, 6, 9);
        BookingResponse later = book("g3", deluxe, 8, 10);
        BookingResponse confirmed = confirm(first);
        assertEquals("CONFIRMED", confirmed.status());
        assertEquals(1, confirmed.autoCancelled());
        assertEquals("admin", confirmed.confirmedBy());
        loginAs("g2", Role.CUSTOMER);
        BookingResponse released = hotel.mine("g2", null).get(0);
        assertEquals("CANCELLED", released.status());
        assertEquals("system", released.cancelledBy());
        assertTrue(released.cancelReason().contains("another guest"));
        loginAs("g3", Role.CUSTOMER);
        assertEquals("PENDING", hotel.mine("g3", null).get(0).status()); // does not overlap
        loginAs("admin", Role.HOTEL_ADMIN);
        assertThrows(InvalidStatusException.class, () -> hotel.confirm(rival.id(), "admin"));
        assertEquals("CONFIRMED", hotel.confirm(later.id(), "admin").status());
    }

    @Test
    void aStayThatOverlapsAConfirmedOneCannotBeConfirmed() {
        BookingResponse pending = book("g1", standard, 5, 8);
        confirmedStay(standard, "g2", 7, 10);
        loginAs("admin", Role.HOTEL_ADMIN);
        assertTrue(assertThrows(ConflictException.class, () -> hotel.confirm(pending.id(), "admin")).getMessage().contains("already confirmed"));
        assertEquals(1, hotel.allBookings(BookingStatus.PENDING, null, null).size());
    }

    @Test
    void onlyPendingBookingsCanBeConfirmedAndFinishedStaysCannot() {
        BookingResponse b = book("g1", standard, 5, 8);
        confirm(b);
        loginAs("admin", Role.HOTEL_ADMIN);
        assertThrows(InvalidStatusException.class, () -> hotel.confirm(b.id(), "admin"));
        Booking past = new Booking(suite, users.findByUsername("g1").orElseThrow(), day(-5), day(-2), 2, new BigDecimal("30000.00"), 3, null);
        past = bookings.save(past);
        Long pastId = past.getId();
        assertThrows(BusinessRuleException.class, () -> hotel.confirm(pastId, "admin"));
        assertThrows(Exception.class, () -> hotel.confirm(99999L, "admin"));
    }

    // ---------- cancelling ----------

    @Test
    void aGuestCancelsBeforeCheckInAndTheRoomIsFreeAgain() {
        BookingResponse b = book("g1", standard, 5, 8);
        confirm(b);
        loginAs("g1", Role.CUSTOMER);
        BookingResponse cancelled = hotel.cancelOwn("g1", b.id(), "  Plans changed ");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("Plans changed", cancelled.cancelReason());
        assertEquals("g1", cancelled.cancelledBy());
        assertFalse(cancelled.canCancel());
        assertEquals(3, hotel.available(day(5), day(8), null, null).size());
        assertThrows(InvalidStatusException.class, () -> hotel.cancelOwn("g1", b.id(), null));
    }

    @Test
    void cancellationIsRestrictedByDateAndOwner() {
        BookingResponse soon = book("g1", standard, 0, 2); // checks in today
        loginAs("g1", Role.CUSTOMER);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> hotel.cancelOwn("g1", soon.id(), null));
        assertTrue(ex.getMessage().contains("before the check-in date"));
        BookingResponse later = book("g1", deluxe, 5, 7);
        loginAs("g2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> hotel.cancelOwn("g2", later.id(), null));
    }

    @Test
    void theHotelNeedsAReasonToCancelAndCannotCancelAFinishedStay() {
        BookingResponse b = book("g1", standard, 5, 8);
        confirm(b);
        loginAs("admin", Role.HOTEL_ADMIN);
        assertEquals("reason", assertThrows(FieldValidationException.class, () -> hotel.adminCancel(b.id(), "  ", "admin")).getField());
        BookingResponse cancelled = hotel.adminCancel(b.id(), "Burst pipe in the room", "admin");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("admin", cancelled.cancelledBy());
        assertThrows(InvalidStatusException.class, () -> hotel.adminCancel(b.id(), "again", "admin"));
        Booking past = bookings.save(new Booking(suite, users.findByUsername("g1").orElseThrow(), day(-5), day(-2), 2, new BigDecimal("30000.00"), 3, null));
        Long pastId = past.getId();
        assertThrows(BusinessRuleException.class, () -> hotel.adminCancel(pastId, "late", "admin"));
    }

    // ---------- rooms ----------

    @Test
    void adminCreatesRoomsWithUniqueNumbersAndEditsThem() {
        loginAs("admin", Role.HOTEL_ADMIN);
        RoomResponse created = hotel.createRoom(new RoomBody(" 305 ", "suite", 3, new BigDecimal("12000"), 4, "Nice", "Wi-Fi, TV ,, Minibar", null));
        assertEquals("305", created.number());
        assertEquals(List.of("Wi-Fi", "TV", "Minibar"), created.amenities());
        assertEquals("number", assertThrows(FieldValidationException.class, () -> hotel.createRoom(new RoomBody("305", "suite", 3, new BigDecimal("12000"), 4, null, null, null))).getField());
        assertEquals("type", assertThrows(FieldValidationException.class, () -> hotel.createRoom(new RoomBody("306", "palace", 3, new BigDecimal("12000"), 4, null, null, null))).getField());
        RoomResponse edited = hotel.updateRoom(created.id(), new RoomBody("305", "suite", 3, new BigDecimal("13500"), 5, "Nicer", null, true));
        assertEquals(0, new BigDecimal("13500.00").compareTo(edited.pricePerNight()));
        assertEquals(5, edited.capacity());
    }

    @Test
    void aRoomWithUpcomingBookingsCannotBeClosed() {
        book("g1", standard, 5, 8);
        loginAs("admin", Role.HOTEL_ADMIN);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> hotel.updateRoom(standard.getId(), new RoomBody("101", "STANDARD", 1, new BigDecimal("3000"), 2, null, null, false)));
        assertTrue(ex.getMessage().contains("upcoming or current bookings"));
        RoomResponse reopened = hotel.updateRoom(closed.getId(), new RoomBody("102", "STANDARD", 1, new BigDecimal("3000"), 2, null, null, true));
        assertTrue(reopened.active());
    }

    // ---------- calendar and dashboard ----------

    @Test
    void theCalendarListsLiveStaysByRoom() {
        BookingResponse stay = book("g1", deluxe, 2, 4);
        confirm(stay);
        book("g2", deluxe, 10, 12);
        BookingResponse dropped = book("g3", suite, 3, 5);
        loginAs("admin", Role.HOTEL_ADMIN);
        hotel.adminCancel(dropped.id(), "Test", "admin");
        YearMonth month = YearMonth.from(day(4)); // the stays above end in this month, whatever today's date
        CalendarResponse calendar = hotel.calendar(month);
        assertEquals(month.lengthOfMonth(), calendar.days());
        assertEquals(4, calendar.rooms().size());
        var deluxeRow = calendar.rooms().stream().filter(r -> r.number().equals("201")).findFirst().orElseThrow();
        assertTrue(deluxeRow.bookings().size() >= 1);
        assertTrue(calendar.rooms().stream().filter(r -> r.number().equals("301")).findFirst().orElseThrow().bookings().isEmpty()); // cancelled is not shown
    }

    @Test
    void dashboardCountsOccupancyArrivalsPendingAndRevenue() {
        confirmedStay(standard, "g1", -1, 2); // in the hotel tonight
        confirmedStay(deluxe, "g2", 0, 3); // arrives today
        confirmedStay(suite, "g3", -3, 0); // leaves today
        book("g4", standard, 20, 22);
        loginAs("admin", Role.HOTEL_ADMIN);
        HotelDashboard d = hotel.dashboard();
        assertEquals(4, d.rooms());
        assertEquals(3, d.activeRooms());
        assertEquals(2, d.occupiedTonight());
        assertEquals(67, d.occupancyPercent());
        assertEquals(1, d.arrivalsToday());
        assertEquals(1, d.departuresToday());
        assertEquals(1, d.pendingBookings());
        assertTrue(d.revenueThisMonth().signum() > 0);
        assertEquals(3, d.byType().size());
        assertEquals(1, d.nextArrivals().size()); // only the guest arriving today; the others arrived earlier
    }

    @Test
    void rolesAreEnforced() {
        loginAs("g1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> hotel.allBookings(null, null, null));
        assertThrows(AccessDeniedException.class, () -> hotel.confirm(1L, "g1"));
        assertThrows(AccessDeniedException.class, () -> hotel.adminCancel(1L, "x", "g1"));
        assertThrows(AccessDeniedException.class, () -> hotel.dashboard());
        assertThrows(AccessDeniedException.class, () -> hotel.createRoom(new RoomBody("9", "SUITE", 1, new BigDecimal("1000"), 1, null, null, null)));
        loginAs("admin", Role.HOTEL_ADMIN);
        assertThrows(AccessDeniedException.class, () -> hotel.book("admin", new BookingRequest(standard.getId(), day(3), day(4), 1, null)));
        assertThrows(AccessDeniedException.class, () -> hotel.mine("admin", null));
    }

    // ---------- concurrency ----------

    @Test
    void twoAdminsConfirmingOverlappingRequestsForOneRoomConfirmExactlyOne() throws Exception {
        List<BookingResponse> rivals = List.of(book("g1", suite, 5, 8), book("g2", suite, 6, 9), book("g3", suite, 7, 10), book("g4", suite, 5, 9));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = rivals.stream().<Callable<Boolean>>map(r -> () -> {
                loginAs("admin", Role.HOTEL_ADMIN);
                try {
                    hotel.confirm(r.id(), "admin");
                    return true;
                } catch (ConflictException | InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, bookings.findAll().stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aDoubleClickedBookingCreatesOneBooking() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("g1", Role.CUSTOMER);
                try {
                    hotel.book("g1", new BookingRequest(deluxe.getId(), day(5), day(8), 2, null));
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
    void aGuestCancellingWhileTheAdminConfirmsEndsConsistently() throws Exception {
        BookingResponse b = book("g1", standard, 5, 8);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> cancel = () -> {
                loginAs("g1", Role.CUSTOMER);
                try {
                    hotel.cancelOwn("g1", b.id(), "changed my mind");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Callable<Boolean> confirm = () -> {
                loginAs("admin", Role.HOTEL_ADMIN);
                try {
                    hotel.confirm(b.id(), "admin");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            long won = winners(pool.invokeAll(List.of(cancel, confirm)));
            BookingStatus end = bookings.findById(b.id()).orElseThrow().getStatus();
            // either the guest cancelled the pending request, or the admin confirmed it and the guest then cancelled the confirmed booking
            assertTrue(end == BookingStatus.CANCELLED || end == BookingStatus.CONFIRMED);
            assertTrue(won >= 1);
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
