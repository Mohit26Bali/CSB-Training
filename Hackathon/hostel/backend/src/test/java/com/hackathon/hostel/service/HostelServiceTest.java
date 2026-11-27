package com.hackathon.hostel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.hackathon.hostel.dto.AllocationResponse;
import com.hackathon.hostel.dto.HostelDashboard;
import com.hackathon.hostel.dto.RequestResponse;
import com.hackathon.hostel.dto.RoomAskBody;
import com.hackathon.hostel.dto.RoomRequestBody;
import com.hackathon.hostel.dto.RoomResponse;
import com.hackathon.hostel.entity.RequestStatus;
import com.hackathon.hostel.entity.Role;
import com.hackathon.hostel.entity.Room;
import com.hackathon.hostel.entity.RoomType;
import com.hackathon.hostel.entity.User;
import com.hackathon.hostel.exception.BusinessRuleException;
import com.hackathon.hostel.exception.ConflictException;
import com.hackathon.hostel.exception.FieldValidationException;
import com.hackathon.hostel.exception.ForbiddenActionException;
import com.hackathon.hostel.exception.InvalidStatusException;
import com.hackathon.hostel.repo.RoomAllocationRepository;
import com.hackathon.hostel.repo.RoomRepository;
import com.hackathon.hostel.repo.RoomRequestRepository;
import com.hackathon.hostel.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class HostelServiceTest {

    @Autowired HostelService hostel;
    @Autowired RoomService roomService;
    @Autowired RoomRepository rooms;
    @Autowired RoomRequestRepository requests;
    @Autowired RoomAllocationRepository allocations;
    @Autowired UserRepository users;

    Room single;
    Room doubleRoom;
    Room closed;

    @BeforeEach
    void setUp() {
        allocations.deleteAll();
        requests.deleteAll();
        rooms.deleteAll();
        users.deleteAll();
        users.save(new User("warden", "x", "Warden", Role.HOSTEL_ADMIN));
        for (int i = 1; i <= 6; i++) {
            users.save(new User("s" + i, "x", "Student " + i, Role.STUDENT));
        }
        single = rooms.save(new Room("A-101", 1, RoomType.SINGLE, 1));
        doubleRoom = rooms.save(new Room("A-102", 1, RoomType.DOUBLE, 2));
        closed = new Room("A-103", 1, RoomType.SINGLE, 1);
        closed.update("A-103", 1, RoomType.SINGLE, 1, false);
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

    RequestResponse ask(String student) {
        loginAs(student, Role.STUDENT);
        return hostel.ask(student, new RoomAskBody(null, "please"));
    }

    RequestResponse approve(RequestResponse request, Room room) {
        loginAs("warden", Role.HOSTEL_ADMIN);
        return hostel.approve(request.id(), room.getId(), "warden");
    }

    // ---------- students ----------

    @Test
    void studentSeesOnlyActiveRoomsWithFreeBedsAndCanFilterByType() {
        loginAs("s1", Role.STUDENT);
        assertEquals(List.of("A-101", "A-102"), roomService.available(null).stream().map(RoomResponse::number).toList());
        assertEquals(List.of("A-102"), roomService.available("double").stream().map(RoomResponse::number).toList());
        assertTrue(roomService.available(null).get(0).occupants().isEmpty()); // no names for students
        assertThrows(FieldValidationException.class, () -> roomService.available("palace"));
    }

    @Test
    void studentRequestsARoomAndSeesTheWaitingListPosition() {
        RequestResponse first = ask("s1");
        RequestResponse second = ask("s2");
        assertEquals("PENDING", first.status());
        assertEquals(1, first.queuePosition());
        assertEquals(2, second.queuePosition());
        loginAs("s2", Role.STUDENT);
        assertEquals(2, hostel.myRequests("s2").get(0).queuePosition());
    }

    @Test
    void onlyOnePendingRequestAtATime() {
        ask("s1");
        assertThrows(ConflictException.class, () -> ask("s1"));
    }

    @Test
    void unknownPreferredTypeIsAFieldError() {
        loginAs("s1", Role.STUDENT);
        FieldValidationException ex = assertThrows(FieldValidationException.class, () -> hostel.ask("s1", new RoomAskBody("castle", null)));
        assertEquals("type", ex.getField());
    }

    @Test
    void cancellingARequestFreesTheQueueSpotAndAllowsANewOne() {
        RequestResponse r = ask("s1");
        ask("s2");
        loginAs("s1", Role.STUDENT);
        assertEquals("CANCELLED", hostel.cancel("s1", r.id()).status());
        assertThrows(InvalidStatusException.class, () -> hostel.cancel("s1", r.id()));
        loginAs("s2", Role.STUDENT);
        assertEquals(1, hostel.myRequests("s2").get(0).queuePosition());
        assertNotNull(ask("s1").id());
    }

    @Test
    void studentCannotCancelSomeoneElsesRequest() {
        RequestResponse r = ask("s1");
        loginAs("s2", Role.STUDENT);
        assertThrows(ForbiddenActionException.class, () -> hostel.cancel("s2", r.id()));
    }

    // ---------- admin decisions ----------

    @Test
    void approvalAllocatesTheRoomAndTheStudentSeesIt() {
        RequestResponse r = ask("s1");
        RequestResponse approved = approve(r, doubleRoom);
        assertEquals("APPROVED", approved.status());
        assertEquals("A-102", approved.roomNumber());
        loginAs("s1", Role.STUDENT);
        AllocationResponse mine = hostel.myAllocation("s1");
        assertEquals("A-102", mine.roomNumber());
        assertTrue(mine.active());
        loginAs("s2", Role.STUDENT);
        assertNull(hostel.myAllocation("s2"));
    }

    @Test
    void studentWithARoomCannotRequestAnother() {
        approve(ask("s1"), doubleRoom);
        assertThrows(ConflictException.class, () -> ask("s1"));
    }

    @Test
    void fullRoomsAndClosedRoomsAreRefused() {
        approve(ask("s1"), single);
        RequestResponse second = ask("s2");
        loginAs("warden", Role.HOSTEL_ADMIN);
        ConflictException full = assertThrows(ConflictException.class, () -> hostel.approve(second.id(), single.getId(), "warden"));
        assertTrue(full.getMessage().contains("full"));
        assertThrows(BusinessRuleException.class, () -> hostel.approve(second.id(), closed.getId(), "warden"));
        FieldValidationException missing = assertThrows(FieldValidationException.class, () -> hostel.approve(second.id(), 9999L, "warden"));
        assertEquals("roomId", missing.getField());
        assertEquals("PENDING", hostel.allRequests(RequestStatus.PENDING).get(0).status()); // untouched by the failures
    }

    @Test
    void decidedRequestsCannotBeDecidedAgain() {
        RequestResponse r = ask("s1");
        approve(r, doubleRoom);
        loginAs("warden", Role.HOSTEL_ADMIN);
        assertThrows(InvalidStatusException.class, () -> hostel.approve(r.id(), doubleRoom.getId(), "warden"));
        assertThrows(InvalidStatusException.class, () -> hostel.reject(r.id(), "no", "warden"));
    }

    @Test
    void rejectionIsRecordedWithItsReason() {
        RequestResponse r = ask("s1");
        loginAs("warden", Role.HOSTEL_ADMIN);
        RequestResponse rejected = hostel.reject(r.id(), "  No rooms this term  ", "warden");
        assertEquals("REJECTED", rejected.status());
        assertEquals("No rooms this term", rejected.decisionReason());
        assertEquals("warden", rejected.decidedBy());
        loginAs("s1", Role.STUDENT);
        assertEquals("REJECTED", hostel.myRequests("s1").get(0).status());
        assertNotNull(ask("s1").id()); // may ask again after a rejection
    }

    @Test
    void vacatingFreesTheBedAndOnlyWorksOnce() {
        RequestResponse r = ask("s1");
        approve(r, single);
        loginAs("warden", Role.HOSTEL_ADMIN);
        AllocationResponse allocation = hostel.allocations(true).get(0);
        assertEquals(0, roomService.available("single").size());
        AllocationResponse left = hostel.vacate(allocation.id());
        assertTrue(left.vacatedAt() != null && !left.active());
        assertEquals(1, roomService.available("single").size());
        assertThrows(InvalidStatusException.class, () -> hostel.vacate(allocation.id()));
        assertEquals(0, hostel.allocations(true).size());
        assertEquals(1, hostel.allocations(false).size());
        loginAs("s1", Role.STUDENT);
        assertNull(hostel.myAllocation("s1"));
    }

    // ---------- rooms ----------

    @Test
    void adminCreatesRoomsWithUniqueNumbers() {
        loginAs("warden", Role.HOSTEL_ADMIN);
        RoomResponse created = roomService.create(new RoomRequestBody(" b-201 ", 2, "triple", 3, null));
        assertEquals("B-201", created.number());
        assertEquals(3, created.bedsLeft());
        FieldValidationException dup = assertThrows(FieldValidationException.class, () -> roomService.create(new RoomRequestBody("B-201", 2, "single", 1, null)));
        assertEquals("number", dup.getField());
        assertThrows(FieldValidationException.class, () -> roomService.create(new RoomRequestBody("B-202", 2, "palace", 1, null)));
    }

    @Test
    void roomCapacityCannotDropBelowItsOccupants() {
        approve(ask("s1"), doubleRoom);
        approve(ask("s2"), doubleRoom);
        loginAs("warden", Role.HOSTEL_ADMIN);
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> roomService.update(doubleRoom.getId(), new RoomRequestBody("A-102", 1, "DOUBLE", 1, true)));
        assertEquals("capacity", ex.getField());
        RoomResponse updated = roomService.update(doubleRoom.getId(), new RoomRequestBody("A-102", 1, "TRIPLE", 3, true));
        assertEquals(1, updated.bedsLeft());
        assertEquals(2, updated.occupants().size());
    }

    @Test
    void dashboardCountsBedsAndRequests() {
        approve(ask("s1"), doubleRoom);
        approve(ask("s2"), single);
        ask("s3");
        loginAs("warden", Role.HOSTEL_ADMIN);
        HostelDashboard d = hostel.dashboard();
        assertEquals(3, d.rooms());
        assertEquals(3, d.beds()); // the closed room's bed does not count
        assertEquals(2, d.occupied());
        assertEquals(1, d.free());
        assertEquals(67, d.occupancyPercent());
        assertEquals(1, d.pendingRequests());
        assertEquals(1, d.fullRooms());
    }

    @Test
    void rolesAreEnforced() {
        loginAs("s1", Role.STUDENT);
        assertThrows(AccessDeniedException.class, () -> roomService.listAll(null));
        assertThrows(AccessDeniedException.class, () -> hostel.dashboard());
        assertThrows(AccessDeniedException.class, () -> hostel.approve(1L, 1L, "s1"));
        loginAs("warden", Role.HOSTEL_ADMIN);
        assertThrows(AccessDeniedException.class, () -> hostel.ask("warden", new RoomAskBody(null, null)));
    }

    // ---------- concurrency ----------

    @Test
    void lastBedGoesToExactlyOneStudentWhenSeveralAdminsApproveAtOnce() throws Exception {
        List<RequestResponse> pending = IntStream.rangeClosed(1, 4).mapToObj(i -> ask("s" + i)).toList();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = pending.stream().<Callable<Boolean>>map(r -> () -> {
                loginAs("warden", Role.HOSTEL_ADMIN);
                try {
                    hostel.approve(r.id(), single.getId(), "warden");
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
            assertEquals(1, allocations.countByRoomAndVacatedAtIsNull(rooms.findById(single.getId()).orElseThrow()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameRequestApprovedIntoTwoRoomsAtOnceAllocatesOnlyOnce() throws Exception {
        RequestResponse r = ask("s1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(room(r, single), room(r, doubleRoom));
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertEquals(1, allocations.findByVacatedAtIsNull().size());
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> room(RequestResponse r, Room target) {
        return () -> {
            loginAs("warden", Role.HOSTEL_ADMIN);
            try {
                hostel.approve(r.id(), target.getId(), "warden");
                return true;
            } catch (InvalidStatusException | ConflictException ex) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    @Test
    void doubleClickingRequestCreatesOnePendingRequest() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("s1", Role.STUDENT);
                try {
                    hostel.ask("s1", new RoomAskBody(null, null));
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
            assertEquals(1, requests.count());
        } finally {
            pool.shutdownNow();
        }
    }
}
