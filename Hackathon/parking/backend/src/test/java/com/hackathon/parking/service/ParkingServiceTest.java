package com.hackathon.parking.service;

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

import com.hackathon.parking.dto.AllocationResponse;
import com.hackathon.parking.dto.AskBody;
import com.hackathon.parking.dto.ParkingDashboard;
import com.hackathon.parking.dto.RequestResponse;
import com.hackathon.parking.dto.SlotBody;
import com.hackathon.parking.dto.SlotResponse;
import com.hackathon.parking.entity.ParkingSlot;
import com.hackathon.parking.entity.RequestStatus;
import com.hackathon.parking.entity.Role;
import com.hackathon.parking.entity.SlotStatus;
import com.hackathon.parking.entity.User;
import com.hackathon.parking.exception.BusinessRuleException;
import com.hackathon.parking.exception.ConflictException;
import com.hackathon.parking.exception.FieldValidationException;
import com.hackathon.parking.exception.ForbiddenActionException;
import com.hackathon.parking.exception.InvalidStatusException;
import com.hackathon.parking.repo.ParkingAllocationRepository;
import com.hackathon.parking.repo.ParkingRequestRepository;
import com.hackathon.parking.repo.SlotRepository;
import com.hackathon.parking.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class ParkingServiceTest {

    @Autowired ParkingService parking;
    @Autowired SlotRepository slots;
    @Autowired ParkingRequestRepository requests;
    @Autowired ParkingAllocationRepository allocations;
    @Autowired UserRepository users;

    ParkingSlot s1;
    ParkingSlot s2;
    ParkingSlot reserved;
    ParkingSlot upstairs;

    @BeforeEach
    void setUp() {
        allocations.deleteAll();
        requests.deleteAll();
        slots.deleteAll();
        users.deleteAll();
        users.save(new User("admin", "x", "Admin", Role.PARKING_ADMIN));
        for (int i = 1; i <= 6; i++) {
            users.save(new User("e" + i, "x", "Employee " + i, Role.EMPLOYEE));
        }
        s1 = slots.save(new ParkingSlot("F1-01", 1, SlotStatus.AVAILABLE));
        s2 = slots.save(new ParkingSlot("F1-02", 1, SlotStatus.AVAILABLE));
        reserved = slots.save(new ParkingSlot("F1-03", 1, SlotStatus.RESERVED));
        upstairs = slots.save(new ParkingSlot("F2-01", 2, SlotStatus.AVAILABLE));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    RequestResponse ask(String employee, String vehicle) {
        loginAs(employee, Role.EMPLOYEE);
        return parking.ask(employee, new AskBody(vehicle, null, "please"));
    }

    RequestResponse approve(RequestResponse request, ParkingSlot slot) {
        loginAs("admin", Role.PARKING_ADMIN);
        return parking.approve(request.id(), slot.getId(), "admin");
    }

    // ---------- employees ----------

    @Test
    void employeeSeesOnlyFreeAvailableSlotsAndCanFilterByFloor() {
        loginAs("e1", Role.EMPLOYEE);
        assertEquals(List.of("F1-01", "F1-02", "F2-01"), parking.freeSlots(null).stream().map(SlotResponse::code).toList());
        assertEquals(List.of("F2-01"), parking.freeSlots(2).stream().map(SlotResponse::code).toList());
        assertEquals(List.of(1, 2), parking.floors());
        assertNull(parking.freeSlots(null).get(0).occupantName());
    }

    @Test
    void requestNormalisesTheVehicleNumberAndJoinsTheQueue() {
        RequestResponse first = ask("e1", " mh 12-ab 1234 ");
        RequestResponse second = ask("e2", "MH14CD5678");
        assertEquals("MH12AB1234", first.vehicleNumber());
        assertEquals(1, first.queuePosition());
        assertEquals(2, second.queuePosition());
    }

    @Test
    void invalidVehicleNumbersAreFieldErrors() {
        for (String bad : List.of("12345678", "ABCDEFGH", "A1", "MH12 AB@1234")) {
            FieldValidationException ex = assertThrows(FieldValidationException.class, () -> ask("e1", bad));
            assertEquals("vehicleNumber", ex.getField());
        }
    }

    @Test
    void onlyOnePendingRequestAndNoDuplicateVehicleOnTheLot() {
        ask("e1", "MH12AB1234");
        assertThrows(ConflictException.class, () -> ask("e1", "MH12AB9999"));
        approve(ask("e2", "MH12AB5555"), s1);
        ConflictException ex = assertThrows(ConflictException.class, () -> ask("e3", "mh12ab5555"));
        assertTrue(ex.getMessage().contains("already parked"));
    }

    @Test
    void cancellingFreesTheQueueAndOnlyOwnerCanCancel() {
        RequestResponse a = ask("e1", "MH12AB1111");
        ask("e2", "MH12AB2222");
        loginAs("e2", Role.EMPLOYEE);
        assertThrows(ForbiddenActionException.class, () -> parking.cancel("e2", a.id()));
        loginAs("e1", Role.EMPLOYEE);
        assertEquals("CANCELLED", parking.cancel("e1", a.id()).status());
        assertThrows(InvalidStatusException.class, () -> parking.cancel("e1", a.id()));
        loginAs("e2", Role.EMPLOYEE);
        assertEquals(1, parking.myRequests("e2").get(0).queuePosition());
    }

    // ---------- admin decisions ----------

    @Test
    void approvalAllocatesTheSlotAndTheEmployeeSeesIt() {
        RequestResponse approved = approve(ask("e1", "MH12AB1234"), s1);
        assertEquals("APPROVED", approved.status());
        assertEquals("F1-01", approved.slotCode());
        loginAs("e1", Role.EMPLOYEE);
        AllocationResponse mine = parking.myAllocation("e1");
        assertEquals("F1-01", mine.slotCode());
        assertEquals("MH12AB1234", mine.vehicleNumber());
        assertEquals(List.of("F1-02", "F2-01"), parking.freeSlots(null).stream().map(SlotResponse::code).toList());
        loginAs("e2", Role.EMPLOYEE);
        assertNull(parking.myAllocation("e2"));
    }

    @Test
    void employeeWithASlotCannotAskAgain() {
        approve(ask("e1", "MH12AB1234"), s1);
        assertThrows(ConflictException.class, () -> ask("e1", "MH12AB1234"));
    }

    @Test
    void takenReservedAndUnknownSlotsAreRefused() {
        approve(ask("e1", "MH12AB1111"), s1);
        RequestResponse second = ask("e2", "MH12AB2222");
        loginAs("admin", Role.PARKING_ADMIN);
        ConflictException taken = assertThrows(ConflictException.class, () -> parking.approve(second.id(), s1.getId(), "admin"));
        assertTrue(taken.getMessage().contains("taken"));
        assertThrows(BusinessRuleException.class, () -> parking.approve(second.id(), reserved.getId(), "admin"));
        FieldValidationException missing = assertThrows(FieldValidationException.class, () -> parking.approve(second.id(), 9999L, "admin"));
        assertEquals("slotId", missing.getField());
        assertEquals("PENDING", parking.allRequests(RequestStatus.PENDING).get(0).status());
    }

    @Test
    void decidedRequestsCannotBeDecidedAgain() {
        RequestResponse r = ask("e1", "MH12AB1234");
        approve(r, s1);
        loginAs("admin", Role.PARKING_ADMIN);
        assertThrows(InvalidStatusException.class, () -> parking.approve(r.id(), s2.getId(), "admin"));
        assertThrows(InvalidStatusException.class, () -> parking.reject(r.id(), "no", "admin"));
    }

    @Test
    void rejectionIsRecordedWithItsReasonAndTheEmployeeMayAskAgain() {
        RequestResponse r = ask("e1", "MH12AB1234");
        loginAs("admin", Role.PARKING_ADMIN);
        RequestResponse rejected = parking.reject(r.id(), "  Lot is full  ", "admin");
        assertEquals("REJECTED", rejected.status());
        assertEquals("Lot is full", rejected.decisionReason());
        assertNotNull(ask("e1", "MH12AB1234").id());
    }

    @Test
    void releaseFreesTheSlotAndWorksOnlyOnce() {
        approve(ask("e1", "MH12AB1234"), s1);
        loginAs("admin", Role.PARKING_ADMIN);
        AllocationResponse allocation = parking.allocations(true).get(0);
        AllocationResponse released = parking.release(allocation.id(), "admin");
        assertTrue(!released.active() && released.releasedAt() != null);
        assertEquals("admin", released.releasedBy());
        assertThrows(InvalidStatusException.class, () -> parking.release(allocation.id(), "admin"));
        assertEquals(0, parking.allocations(true).size());
        assertEquals(1, parking.allocations(false).size());
        loginAs("e1", Role.EMPLOYEE);
        assertEquals(3, parking.freeSlots(null).size());
        assertNotNull(ask("e1", "MH12AB1234").id()); // may park again, vehicle is no longer on the lot
    }

    // ---------- slots ----------

    @Test
    void adminCreatesSlotsWithUniqueCodes() {
        loginAs("admin", Role.PARKING_ADMIN);
        SlotResponse created = parking.createSlot(new SlotBody(" f3-09 ", 3, null));
        assertEquals("F3-09", created.code());
        assertEquals("AVAILABLE", created.status());
        FieldValidationException dup = assertThrows(FieldValidationException.class, () -> parking.createSlot(new SlotBody("F3-09", 3, null)));
        assertEquals("code", dup.getField());
        assertThrows(FieldValidationException.class, () -> parking.createSlot(new SlotBody("F3-10", 3, "haunted")));
    }

    @Test
    void anOccupiedSlotCannotBeMarkedReservedOrMaintenance() {
        approve(ask("e1", "MH12AB1234"), s1);
        loginAs("admin", Role.PARKING_ADMIN);
        assertThrows(BusinessRuleException.class, () -> parking.updateSlot(s1.getId(), new SlotBody("F1-01", 1, "maintenance")));
        SlotResponse moved = parking.updateSlot(s1.getId(), new SlotBody("F1-01A", 1, "available"));
        assertTrue(moved.occupied());
        assertEquals("Employee 1", moved.occupantName());
        SlotResponse maintenance = parking.updateSlot(s2.getId(), new SlotBody("F1-02", 1, "maintenance"));
        assertEquals("MAINTENANCE", maintenance.status());
        loginAs("e1", Role.EMPLOYEE);
        assertEquals(List.of("F2-01"), parking.freeSlots(null).stream().map(SlotResponse::code).toList());
    }

    @Test
    void dashboardIsFloorWise() {
        approve(ask("e1", "MH12AB1111"), s1);
        approve(ask("e2", "MH12AB2222"), upstairs);
        ask("e3", "MH12AB3333");
        loginAs("admin", Role.PARKING_ADMIN);
        ParkingDashboard d = parking.dashboard();
        assertEquals(4, d.slots());
        assertEquals(2, d.occupied());
        assertEquals(1, d.free());
        assertEquals(1, d.reserved());
        assertEquals(67, d.occupancyPercent());
        assertEquals(1, d.pendingRequests());
        assertEquals(2, d.byFloor().size());
        assertEquals(1, d.byFloor().get(0).free()); // floor 1: F1-02 is the only free one
    }

    @Test
    void rolesAreEnforced() {
        loginAs("e1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> parking.allSlots(null));
        assertThrows(AccessDeniedException.class, () -> parking.dashboard());
        assertThrows(AccessDeniedException.class, () -> parking.approve(1L, 1L, "e1"));
        loginAs("admin", Role.PARKING_ADMIN);
        assertThrows(AccessDeniedException.class, () -> parking.ask("admin", new AskBody("MH12AB1234", null, null)));
    }

    // ---------- concurrency ----------

    @Test
    void lastSlotGoesToExactlyOneEmployeeWhenSeveralAdminsApproveAtOnce() throws Exception {
        List<RequestResponse> pending = IntStream.rangeClosed(1, 4).mapToObj(i -> ask("e" + i, "MH12AB100" + i)).toList();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = pending.stream().<Callable<Boolean>>map(r -> approveTask(r, s1)).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, allocations.findByReleasedAtIsNull().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameRequestApprovedIntoTwoSlotsAtOnceAllocatesOnlyOnce() throws Exception {
        RequestResponse r = ask("e1", "MH12AB1234");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            assertEquals(1, winners(pool.invokeAll(List.of(approveTask(r, s1), approveTask(r, s2)))));
            assertEquals(1, allocations.findByReleasedAtIsNull().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void doubleClickingRequestCreatesOnePendingRequest() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("e1", Role.EMPLOYEE);
                try {
                    parking.ask("e1", new AskBody("MH12AB1234", null, null));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, requests.count());
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> approveTask(RequestResponse r, ParkingSlot slot) {
        return () -> {
            loginAs("admin", Role.PARKING_ADMIN);
            try {
                parking.approve(r.id(), slot.getId(), "admin");
                return true;
            } catch (InvalidStatusException | ConflictException ex) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
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
