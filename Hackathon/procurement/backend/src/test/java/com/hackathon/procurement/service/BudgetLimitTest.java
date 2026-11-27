package com.hackathon.procurement.service;

import static com.hackathon.procurement.service.ProcurementServiceTest.loginAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.hackathon.procurement.dto.PurchaseResponse;
import com.hackathon.procurement.dto.RequestSubmission;
import com.hackathon.procurement.entity.RequestStatus;
import com.hackathon.procurement.entity.Role;
import com.hackathon.procurement.entity.User;
import com.hackathon.procurement.exception.ConflictException;
import com.hackathon.procurement.repo.PurchaseRequestRepository;
import com.hackathon.procurement.repo.UserRepository;

/** Runs with a small monthly budget of 100,000 so the limit is easy to hit. */
@SpringBootTest(properties = "app.budget.monthly-limit=100000")
@ActiveProfiles("test")
class BudgetLimitTest {

    @Autowired ProcurementService service;
    @Autowired PurchaseRequestRepository requests;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        requests.deleteAll();
        users.deleteAll();
        users.save(new User("pm", "x", "Procurement One", Role.PROCUREMENT_MANAGER));
        users.save(new User("emp", "x", "Employee", Role.EMPLOYEE));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private PurchaseResponse submit(int quantity, String unitCost) {
        loginAs("emp", Role.EMPLOYEE);
        return service.submit("emp", new RequestSubmission("Laptop", "HARDWARE", quantity, new BigDecimal(unitCost), "Needed"));
    }

    @Test
    void approvalIsRefusedWhenItWouldExceedTheMonthlyBudget() {
        PurchaseResponse big = submit(1, "60000");
        PurchaseResponse tooMuch = submit(1, "50000");
        PurchaseResponse fits = submit(1, "40000");

        loginAs("pm", Role.PROCUREMENT_MANAGER);
        service.approve(big.id(), "pm");
        ConflictException ex = assertThrows(ConflictException.class, () -> service.approve(tooMuch.id(), "pm"));
        assertTrue(ex.getMessage().contains("exceed"), ex.getMessage());
        assertTrue(ex.getMessage().contains("40,000.00"), "should name the remaining amount: " + ex.getMessage());
        assertEquals(RequestStatus.PENDING, requests.findById(tooMuch.id()).orElseThrow().getStatus());

        service.approve(fits.id(), "pm"); // exactly reaches the limit
        assertEquals(0, BigDecimal.ZERO.compareTo(service.dashboard().remainingThisMonth()));
    }

    @Test
    void rejectedRequestsDoNotUseUpTheBudget() {
        PurchaseResponse a = submit(1, "90000");
        PurchaseResponse b = submit(1, "90000");
        loginAs("pm", Role.PROCUREMENT_MANAGER);
        service.reject(a.id(), "No", "pm");
        assertEquals("APPROVED", service.approve(b.id(), "pm").status());
    }

    @Test
    void fiveApprovalsRacingForTheBudgetNeverOverspend() throws Exception {
        // each request is 30,000; only three fit into 100,000
        List<PurchaseResponse> pending = IntStream.range(0, 5).mapToObj(i -> submit(1, "30000")).toList();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> jobs = pending.stream().<Callable<Boolean>>map(p -> () -> {
                loginAs("pm", Role.PROCUREMENT_MANAGER);
                try {
                    service.approve(p.id(), "pm");
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            long approved = 0;
            for (Future<Boolean> f : pool.invokeAll(jobs)) {
                if (f.get()) {
                    approved++;
                }
            }
            assertEquals(3, approved);
            loginAs("pm", Role.PROCUREMENT_MANAGER);
            assertEquals(0, new BigDecimal("90000.00").compareTo(service.dashboard().spentThisMonth()));
        } finally {
            pool.shutdownNow();
        }
    }
}
