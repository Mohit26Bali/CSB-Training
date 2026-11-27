package com.hackathon.procurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
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

import com.hackathon.procurement.dto.ProcurementDashboard;
import com.hackathon.procurement.dto.PurchaseResponse;
import com.hackathon.procurement.dto.RequestSubmission;
import com.hackathon.procurement.dto.StatusTotal;
import com.hackathon.procurement.entity.ItemCategory;
import com.hackathon.procurement.entity.PurchaseRequest;
import com.hackathon.procurement.entity.RequestStatus;
import com.hackathon.procurement.entity.Role;
import com.hackathon.procurement.entity.User;
import com.hackathon.procurement.exception.FieldValidationException;
import com.hackathon.procurement.exception.ForbiddenActionException;
import com.hackathon.procurement.exception.InvalidStatusException;
import com.hackathon.procurement.exception.ResourceNotFoundException;
import com.hackathon.procurement.repo.PurchaseRequestRepository;
import com.hackathon.procurement.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class ProcurementServiceTest {

    @Autowired ProcurementService service;
    @Autowired PurchaseRequestRepository requests;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        requests.deleteAll();
        users.deleteAll();
        users.save(new User("pm", "x", "Procurement One", Role.PROCUREMENT_MANAGER));
        users.save(new User("pm2", "x", "Procurement Two", Role.PROCUREMENT_MANAGER));
        users.save(new User("emp1", "x", "Employee One", Role.EMPLOYEE));
        users.save(new User("emp2", "x", "Employee Two", Role.EMPLOYEE));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    private PurchaseResponse submit(String who, int quantity, String unitCost) {
        loginAs(who, Role.EMPLOYEE);
        return service.submit(who, new RequestSubmission("Monitor", "HARDWARE", quantity, new BigDecimal(unitCost), "Needed for the team"));
    }

    // ----- submitting -----

    @Test
    void employeeSubmitsAPendingRequestWithATotal() {
        PurchaseResponse r = submit("emp1", 3, "12000.50");
        assertEquals("PENDING", r.status());
        assertEquals(0, new BigDecimal("36001.50").compareTo(r.estimatedTotal()));
        assertEquals("emp1", r.employeeUsername());
    }

    @Test
    void unknownCategoryIsRejected() {
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals("category", assertThrows(FieldValidationException.class, () -> service.submit("emp1",
                new RequestSubmission("Thing", "YACHTS", 1, BigDecimal.TEN, "Because"))).getField());
    }

    @Test
    void managersCannotSubmitAndEmployeesCannotUseManagerFeatures() {
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        assertThrows(AccessDeniedException.class, () -> service.submit("pm",
                new RequestSubmission("Thing", "OTHER", 1, BigDecimal.TEN, "Because")));
        PurchaseResponse r = submit("emp1", 1, "100");
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> service.allRequests(null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.approve(r.id(), "emp1"));
        assertThrows(AccessDeniedException.class, () -> service.markOrdered(r.id(), null));
        assertThrows(AccessDeniedException.class, () -> service.markDelivered(r.id()));
        assertThrows(AccessDeniedException.class, () -> service.dashboard());
    }

    @Test
    void employeesSeeOnlyTheirOwnRequests() {
        submit("emp1", 1, "100");
        submit("emp2", 1, "200");
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals(1, service.myRequests("emp1", null, null, null).size());
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        assertEquals(2, service.allRequests(null, null, null).size());
        assertEquals(1, service.allRequests(null, "hardware", "employee two").size());
        assertThrows(FieldValidationException.class, () -> service.allRequests(null, "NOPE", null));
    }

    // ----- deciding -----

    @Test
    void approvingRecordsWhoAndWhen() {
        PurchaseResponse r = submit("emp1", 2, "500");
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        PurchaseResponse approved = service.approve(r.id(), "pm");
        assertEquals("APPROVED", approved.status());
        assertEquals("pm", approved.decidedBy());
        assertNotNull(approved.decidedAt());
    }

    @Test
    void rejectingNeedsAReason() {
        PurchaseResponse r = submit("emp1", 2, "500");
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        assertEquals("reason", assertThrows(FieldValidationException.class, () -> service.reject(r.id(), " ", "pm")).getField());
        PurchaseResponse rejected = service.reject(r.id(), " Over budget ", "pm");
        assertEquals("REJECTED", rejected.status());
        assertEquals("Over budget", rejected.rejectionReason());
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        PurchaseResponse r = submit("emp1", 1, "100");
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        service.approve(r.id(), "pm");
        assertThrows(InvalidStatusException.class, () -> service.approve(r.id(), "pm2"));
        assertThrows(InvalidStatusException.class, () -> service.reject(r.id(), "No", "pm2"));
    }

    @Test
    void aManagerCannotDecideTheirOwnRequestAndUnknownIdsAre404() {
        User pm = users.findByUsername("pm").orElseThrow();
        Long id = requests.save(new PurchaseRequest(pm, "Own thing", ItemCategory.OTHER, 1, BigDecimal.TEN, "Mine")).getId();
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        assertThrows(ForbiddenActionException.class, () -> service.approve(id, "pm"));
        assertThrows(ResourceNotFoundException.class, () -> service.approve(999_999L, "pm"));
    }

    // ----- ordering and delivery -----

    @Test
    void approvedRequestsGoThroughOrderedToDelivered() {
        PurchaseResponse r = submit("emp1", 1, "100");
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        service.approve(r.id(), "pm");

        PurchaseResponse ordered = service.markOrdered(r.id(), " PO-7788 ");
        assertEquals("ORDERED", ordered.status());
        assertEquals("PO-7788", ordered.orderReference());
        assertNotNull(ordered.orderedAt());

        PurchaseResponse delivered = service.markDelivered(r.id());
        assertEquals("DELIVERED", delivered.status());
        assertNotNull(delivered.deliveredAt());
    }

    @Test
    void rejectedOrPendingRequestsCannotBeOrdered() {
        PurchaseResponse rejected = submit("emp1", 1, "100");
        PurchaseResponse pending = submit("emp1", 1, "100");
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        service.reject(rejected.id(), "No", "pm");
        assertThrows(InvalidStatusException.class, () -> service.markOrdered(rejected.id(), null));
        assertThrows(InvalidStatusException.class, () -> service.markOrdered(pending.id(), null));
    }

    @Test
    void stepsCannotBeSkippedOrRepeated() {
        PurchaseResponse r = submit("emp1", 1, "100");
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        assertThrows(InvalidStatusException.class, () -> service.markDelivered(r.id())); // still pending
        service.approve(r.id(), "pm");
        assertThrows(InvalidStatusException.class, () -> service.markDelivered(r.id())); // approved, not ordered
        service.markOrdered(r.id(), null);
        assertNull(service.markDelivered(r.id()).orderReference());
        assertThrows(InvalidStatusException.class, () -> service.markOrdered(r.id(), null)); // already delivered
        assertThrows(InvalidStatusException.class, () -> service.markDelivered(r.id()));
    }

    // ----- dashboard -----

    @Test
    void dashboardAddsUpSpendAndStatuses() {
        PurchaseResponse a = submit("emp1", 2, "1000"); // 2000
        PurchaseResponse b = submit("emp1", 1, "500");  // 500
        submit("emp2", 1, "300");                        // stays pending
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        service.approve(a.id(), "pm");
        service.reject(b.id(), "No", "pm");

        ProcurementDashboard d = service.dashboard();
        assertEquals(0, new BigDecimal("2000.00").compareTo(d.spentThisMonth()));
        assertEquals(0, new BigDecimal("498000.00").compareTo(d.remainingThisMonth()));
        assertEquals(1, d.spendByCategory().size());
        assertNotNull(d.oldestPendingAt());
        StatusTotal pending = d.byStatus().stream().filter(s -> s.status().equals("PENDING")).findFirst().orElseThrow();
        assertEquals(1, pending.count());
        assertEquals(5, d.byStatus().size());

        loginAs("emp1", Role.EMPLOYEE);
        assertEquals(0, service.mySummary("emp1").stream().filter(s -> s.status().equals("PENDING")).findFirst().orElseThrow().count());
    }

    // ----- concurrency -----

    @Test
    void twoManagersCannotDecideTheSameRequestAtOnce() throws Exception {
        PurchaseResponse r = submit("emp1", 1, "100");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                String manager = i % 2 == 0 ? "pm" : "pm2";
                loginAs(manager, Role.PROCUREMENT_MANAGER);
                try {
                    if (i < 2) {
                        service.approve(r.id(), manager);
                    } else {
                        service.reject(r.id(), "Too late", manager);
                    }
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(jobs)) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }

    static RequestStatus statusOf(PurchaseRequestRepository repo, Long id) {
        return repo.findById(id).orElseThrow().getStatus();
    }
}
