package com.hackathon.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import com.hackathon.expense.dto.ExpenseRequest;
import com.hackathon.expense.dto.ExpenseResponse;
import com.hackathon.expense.dto.ManagerDashboard;
import com.hackathon.expense.dto.StatusSummary;
import com.hackathon.expense.entity.ExpenseStatus;
import com.hackathon.expense.entity.Role;
import com.hackathon.expense.entity.User;
import com.hackathon.expense.exception.FieldValidationException;
import com.hackathon.expense.exception.ForbiddenActionException;
import com.hackathon.expense.exception.InvalidStatusException;
import com.hackathon.expense.exception.ResourceNotFoundException;
import com.hackathon.expense.repo.ExpenseRepository;
import com.hackathon.expense.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class ExpenseServiceTest {

    @Autowired ExpenseService service;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("mgr", "x", "Manager One", Role.MANAGER));
        userRepository.save(new User("mgr2", "x", "Manager Two", Role.MANAGER));
        IntStream.rangeClosed(1, 6)
                .forEach(i -> userRepository.save(new User("emp" + i, "x", "Employee " + i, Role.EMPLOYEE)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    private ExpenseResponse submit(String employee, String amount) {
        loginAs(employee, Role.EMPLOYEE);
        return service.submit(employee, request(LocalDate.now(), "TRAVEL", amount));
    }

    private static ExpenseRequest request(LocalDate date, String category, String amount) {
        return new ExpenseRequest(date, category, new BigDecimal(amount), "Client visit");
    }

    // ----- submitting -----

    @Test
    void employeeSubmitsAPendingExpense() {
        ExpenseResponse e = submit("emp1", "1250.50");
        assertEquals("PENDING", e.status());
        assertEquals("emp1", e.employeeUsername());
        assertEquals(0, new BigDecimal("1250.50").compareTo(e.amount()));
        assertNotNull(e.submittedAt());
    }

    @Test
    void expenseDateInTheFutureIsRejected() {
        loginAs("emp1", Role.EMPLOYEE);
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.submit("emp1", request(LocalDate.now().plusDays(1), "MEALS", "100")));
        assertEquals("expenseDate", ex.getField());
    }

    @Test
    void unknownCategoryIsRejected() {
        loginAs("emp1", Role.EMPLOYEE);
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.submit("emp1", request(LocalDate.now(), "YACHTS", "100")));
        assertEquals("category", ex.getField());
    }

    @Test
    void managersCannotSubmitExpenses() {
        loginAs("mgr", Role.MANAGER);
        assertThrows(AccessDeniedException.class,
                () -> service.submit("mgr", request(LocalDate.now(), "TRAVEL", "100")));
    }

    // ----- visibility -----

    @Test
    void employeesSeeOnlyTheirOwnExpenses() {
        submit("emp1", "100");
        submit("emp2", "200");
        loginAs("emp1", Role.EMPLOYEE);
        List<ExpenseResponse> mine = service.myExpenses("emp1", null, null, null);
        assertEquals(1, mine.size());
        assertEquals("emp1", mine.get(0).employeeUsername());
    }

    @Test
    void employeesCannotUseManagerFeatures() {
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> service.allExpenses(null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.approve(1L, "emp1"));
        assertThrows(AccessDeniedException.class, () -> service.dashboard());
    }

    @Test
    void filtersByStatusCategoryAndText() {
        loginAs("emp1", Role.EMPLOYEE);
        service.submit("emp1", new ExpenseRequest(LocalDate.now(), "MEALS", new BigDecimal("300"), "Team lunch"));
        service.submit("emp1", new ExpenseRequest(LocalDate.now(), "TRAVEL", new BigDecimal("900"), "Cab to airport"));
        assertEquals(1, service.myExpenses("emp1", null, "meals", null).size());
        assertEquals(1, service.myExpenses("emp1", null, null, "AIRPORT").size());
        assertEquals(0, service.myExpenses("emp1", ExpenseStatus.APPROVED, null, null).size());
        assertThrows(FieldValidationException.class, () -> service.myExpenses("emp1", null, "NOPE", null));
    }

    // ----- approving and rejecting -----

    @Test
    void managerApprovesAndTheDecisionIsRecorded() {
        ExpenseResponse e = submit("emp1", "500");
        loginAs("mgr", Role.MANAGER);
        ExpenseResponse approved = service.approve(e.id(), "mgr");
        assertEquals("APPROVED", approved.status());
        assertEquals("mgr", approved.decidedBy());
        assertNotNull(approved.decidedAt());
    }

    @Test
    void rejectionNeedsAReason() {
        ExpenseResponse e = submit("emp1", "500");
        loginAs("mgr", Role.MANAGER);
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.reject(e.id(), "   ", "mgr"));
        assertEquals("reason", ex.getField());

        ExpenseResponse rejected = service.reject(e.id(), "  Missing receipt ", "mgr");
        assertEquals("REJECTED", rejected.status());
        assertEquals("Missing receipt", rejected.rejectionReason());
    }

    @Test
    void aProcessedExpenseCannotBeProcessedAgain() {
        ExpenseResponse e = submit("emp1", "500");
        loginAs("mgr", Role.MANAGER);
        service.approve(e.id(), "mgr");
        assertThrows(InvalidStatusException.class, () -> service.approve(e.id(), "mgr2"));
        assertThrows(InvalidStatusException.class, () -> service.reject(e.id(), "Changed my mind", "mgr2"));
    }

    @Test
    void aManagerCannotDecideTheirOwnExpense() {
        // Managers cannot submit through the API, but the rule is enforced anyway (defence in depth).
        User manager = userRepository.findByUsername("mgr").orElseThrow();
        Long id = expenseRepository.save(new com.hackathon.expense.entity.Expense(manager, LocalDate.now(),
                com.hackathon.expense.entity.ExpenseCategory.OTHER, new BigDecimal("10"), "Own claim")).getId();
        loginAs("mgr", Role.MANAGER);
        assertThrows(ForbiddenActionException.class, () -> service.approve(id, "mgr"));
    }

    @Test
    void unknownExpenseIs404() {
        loginAs("mgr", Role.MANAGER);
        assertThrows(ResourceNotFoundException.class, () -> service.approve(999_999L, "mgr"));
    }

    // ----- numbers -----

    @Test
    void summaryAndDashboardAddUpCorrectly() {
        ExpenseResponse a = submit("emp1", "1000.00");
        ExpenseResponse b = submit("emp1", "250.25");
        submit("emp2", "75.00");
        loginAs("mgr", Role.MANAGER);
        service.approve(a.id(), "mgr");
        service.reject(b.id(), "Not allowed", "mgr");

        ManagerDashboard dashboard = service.dashboard();
        StatusSummary s = dashboard.summary();
        assertEquals(1, s.pendingCount());
        assertEquals(0, new BigDecimal("75.00").compareTo(s.pendingAmount()));
        assertEquals(1, s.approvedCount());
        assertEquals(0, new BigDecimal("1000.00").compareTo(s.approvedAmount()));
        assertEquals(1, s.rejectedCount());
        assertEquals(1, dashboard.approvedByCategory().size());
        assertNotNull(dashboard.oldestPendingAt());

        loginAs("emp1", Role.EMPLOYEE);
        StatusSummary mine = service.mySummary("emp1");
        assertEquals(0, mine.pendingCount());
        assertEquals(1, mine.approvedCount());
    }

    // ----- concurrency -----

    @Test
    void twoManagersCannotProcessTheSameExpenseAtOnce() throws Exception {
        ExpenseResponse e = submit("emp1", "500");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                String manager = i % 2 == 0 ? "mgr" : "mgr2";
                loginAs(manager, Role.MANAGER);
                try {
                    if (i < 2) {
                        service.approve(e.id(), manager);
                    } else {
                        service.reject(e.id(), "Too late", manager);
                    }
                    return true;
                } catch (InvalidStatusException ex) {
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
            ExpenseStatus finalStatus = expenseRepository.findById(e.id()).orElseThrow().getStatus();
            assertTrue(finalStatus == ExpenseStatus.APPROVED || finalStatus == ExpenseStatus.REJECTED);
        } finally {
            pool.shutdownNow();
        }
    }
}
