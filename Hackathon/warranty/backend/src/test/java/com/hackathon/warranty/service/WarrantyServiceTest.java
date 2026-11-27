package com.hackathon.warranty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.hackathon.warranty.dto.ClaimRequest;
import com.hackathon.warranty.dto.ClaimResponse;
import com.hackathon.warranty.dto.ProductRequest;
import com.hackathon.warranty.dto.ProductResponse;
import com.hackathon.warranty.entity.ClaimStatus;
import com.hackathon.warranty.entity.Product;
import com.hackathon.warranty.entity.ProductCategory;
import com.hackathon.warranty.entity.Role;
import com.hackathon.warranty.entity.User;
import com.hackathon.warranty.exception.BusinessRuleException;
import com.hackathon.warranty.exception.ConflictException;
import com.hackathon.warranty.exception.FieldValidationException;
import com.hackathon.warranty.exception.InvalidStatusException;
import com.hackathon.warranty.exception.ResourceNotFoundException;
import com.hackathon.warranty.repo.ClaimRepository;
import com.hackathon.warranty.repo.ProductRepository;
import com.hackathon.warranty.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class WarrantyServiceTest {

    @Autowired ProductService productService;
    @Autowired ClaimService claimService;
    @Autowired ProductRepository products;
    @Autowired ClaimRepository claims;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        claims.deleteAll();
        products.deleteAll();
        users.deleteAll();
        users.save(new User("agent", "x", "Agent One", Role.SERVICE_AGENT));
        users.save(new User("agent2", "x", "Agent Two", Role.SERVICE_AGENT));
        users.save(new User("cust1", "x", "Customer One", Role.CUSTOMER));
        users.save(new User("cust2", "x", "Customer Two", Role.CUSTOMER));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    /** A product bought {@code monthsAgo} months ago with a warranty of {@code months} months. */
    private Product product(String owner, String serial, int monthsAgo, int months) {
        User user = users.findByUsername(owner).orElseThrow();
        return products.save(new Product(user, "Laptop", "Acme", ProductCategory.LAPTOP, serial,
                LocalDate.now().minusMonths(monthsAgo), months));
    }

    private ProductRequest registration(String serial, LocalDate bought, int months) {
        return new ProductRequest("Phone", "Acme", "MOBILE", serial, bought, months);
    }

    private ClaimResponse file(String customer, Product p) {
        loginAs(customer, Role.CUSTOMER);
        return claimService.file(customer, new ClaimRequest(p.getId(), "It stopped working", null));
    }

    // ----- registering products -----

    @Test
    void customerRegistersAProductAndItsWarrantyIsWorkedOut() {
        loginAs("cust1", Role.CUSTOMER);
        ProductResponse r = productService.register("cust1", registration(" ab-123 ", LocalDate.now().minusMonths(6), 12));
        assertEquals("AB-123", r.serialNumber());
        assertEquals(LocalDate.now().minusMonths(6).plusMonths(12), r.warrantyEnd());
        assertTrue(r.warrantyActive());
        assertFalse(r.hasActiveClaim());
        assertTrue(r.daysLeft() > 150);
        assertFalse(r.expiringSoon());
    }

    @Test
    void warrantyStatesActiveExpiringSoonAndExpired() {
        loginAs("cust1", Role.CUSTOMER);
        ProductResponse soon = productService.register("cust1", registration("SOON-1", LocalDate.now().plusDays(10).minusMonths(12), 12));
        ProductResponse expired = productService.register("cust1", registration("OLD-1", LocalDate.now().minusMonths(14), 12));
        ProductResponse lastDay = productService.register("cust1", registration("LAST-1", LocalDate.now().minusMonths(12), 12));
        assertTrue(soon.warrantyActive());
        assertTrue(soon.expiringSoon());
        assertFalse(expired.warrantyActive());
        assertFalse(expired.expiringSoon());
        assertTrue(expired.daysLeft() < 0);
        assertTrue(lastDay.warrantyActive(), "the last day of cover still counts");
        assertEquals(0, lastDay.daysLeft());
    }

    @Test
    void serialNumbersAreUniqueIgnoringCaseAcrossCustomers() {
        loginAs("cust1", Role.CUSTOMER);
        productService.register("cust1", registration("SN-1", LocalDate.now().minusMonths(1), 12));
        assertEquals("serialNumber", assertThrows(FieldValidationException.class,
                () -> productService.register("cust1", registration("sn-1", LocalDate.now().minusMonths(1), 12))).getField());
        loginAs("cust2", Role.CUSTOMER);
        assertEquals("serialNumber", assertThrows(FieldValidationException.class,
                () -> productService.register("cust2", registration("SN-1", LocalDate.now().minusMonths(1), 12))).getField());
    }

    @Test
    void purchaseDateAndCategoryAreValidated() {
        loginAs("cust1", Role.CUSTOMER);
        assertEquals("purchaseDate", assertThrows(FieldValidationException.class,
                () -> productService.register("cust1", registration("F-1", LocalDate.now().plusDays(1), 12))).getField());
        assertEquals("purchaseDate", assertThrows(FieldValidationException.class,
                () -> productService.register("cust1", registration("F-2", LocalDate.now().minusYears(11), 12))).getField());
        assertEquals("category", assertThrows(FieldValidationException.class, () -> productService.register("cust1",
                new ProductRequest("X", "Y", "YACHT", "F-3", LocalDate.now(), 12))).getField());
    }

    @Test
    void customersSeeAndSearchOnlyTheirOwnProducts() {
        product("cust1", "S-1", 1, 12);
        product("cust2", "S-2", 1, 12);
        loginAs("cust1", Role.CUSTOMER);
        assertEquals(1, productService.myProducts("cust1", null).size());
        assertEquals(1, productService.myProducts("cust1", "acme").size());
        assertEquals(1, productService.myProducts("cust1", "s-1").size());
        assertEquals(0, productService.myProducts("cust1", "s-2").size());
    }

    @Test
    void agentsCannotRegisterProductsAndCustomersCannotUseAgentFeatures() {
        loginAs("agent", Role.SERVICE_AGENT);
        assertThrows(AccessDeniedException.class, () -> productService.register("agent", registration("A-1", LocalDate.now(), 12)));
        assertThrows(AccessDeniedException.class, () -> claimService.file("agent", new ClaimRequest(1L, "x", null)));
        loginAs("cust1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> claimService.list(null, null, null));
        assertThrows(AccessDeniedException.class, () -> claimService.approve(1L, "cust1"));
        assertThrows(AccessDeniedException.class, () -> claimService.summary());
    }

    // ----- filing claims -----

    @Test
    void aClaimOnAProductUnderWarrantyIsPendingWithMediumPriority() {
        Product p = product("cust1", "C-1", 2, 12);
        ClaimResponse c = file("cust1", p);
        assertEquals("PENDING", c.status());
        assertEquals("MEDIUM", c.priority());
        assertEquals("cust1", c.customerUsername());
    }

    @Test
    void anExpiredWarrantyCannotGenerateAClaim() {
        Product expired = product("cust1", "E-1", 14, 12);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> file("cust1", expired));
        assertTrue(ex.getMessage().contains("expired"));
        assertEquals(0, claims.count());
    }

    @Test
    void aSecondActiveClaimForTheSameProductIsRefusedButAFinishedOneDoesNotBlock() {
        Product p = product("cust1", "D-1", 2, 12);
        ClaimResponse first = file("cust1", p);
        assertThrows(ConflictException.class, () -> file("cust1", p)); // pending blocks

        loginAs("agent", Role.SERVICE_AGENT);
        claimService.approve(first.id(), "agent");
        assertThrows(ConflictException.class, () -> file("cust1", p)); // approved blocks
        loginAs("agent", Role.SERVICE_AGENT);
        claimService.startRepair(first.id());
        assertThrows(ConflictException.class, () -> file("cust1", p)); // in repair blocks
        loginAs("agent", Role.SERVICE_AGENT);
        claimService.complete(first.id(), "Fixed");

        assertEquals("PENDING", file("cust1", p).status()); // completed no longer blocks
    }

    @Test
    void aRejectedClaimDoesNotBlockANewOne() {
        Product p = product("cust1", "R-1", 2, 12);
        ClaimResponse first = file("cust1", p);
        loginAs("agent", Role.SERVICE_AGENT);
        claimService.reject(first.id(), "agent", "Water damage");
        assertEquals("PENDING", file("cust1", p).status());
    }

    @Test
    void youCannotClaimOnSomeoneElsesProductOrAnUnknownOne() {
        Product theirs = product("cust2", "T-1", 2, 12);
        assertThrows(ResourceNotFoundException.class, () -> file("cust1", theirs));
        loginAs("cust1", Role.CUSTOMER);
        assertThrows(ResourceNotFoundException.class, () -> claimService.file("cust1", new ClaimRequest(999_999L, "x", null)));
    }

    @Test
    void priorityIsValidated() {
        Product p = product("cust1", "P-1", 2, 12);
        loginAs("cust1", Role.CUSTOMER);
        assertEquals("priority", assertThrows(FieldValidationException.class,
                () -> claimService.file("cust1", new ClaimRequest(p.getId(), "x", "URGENT"))).getField());
        assertEquals("HIGH", claimService.file("cust1", new ClaimRequest(p.getId(), "x", "high")).priority());
    }

    // ----- agent workflow -----

    @Test
    void approvedClaimsGoToRepairAndThenComplete() {
        ClaimResponse c = file("cust1", product("cust1", "W-1", 2, 12));
        loginAs("agent", Role.SERVICE_AGENT);

        ClaimResponse approved = claimService.approve(c.id(), "agent");
        assertEquals("APPROVED", approved.status());
        assertEquals("agent", approved.decidedBy());
        assertNotNull(approved.decidedAt());

        ClaimResponse repair = claimService.startRepair(c.id());
        assertEquals("IN_REPAIR", repair.status());
        assertNotNull(repair.repairStartedAt());

        ClaimResponse done = claimService.complete(c.id(), " Replaced the board ");
        assertEquals("COMPLETED", done.status());
        assertEquals("Replaced the board", done.resolutionNote());
        assertNotNull(done.completedAt());
    }

    @Test
    void completingWithoutANoteIsAllowed() {
        ClaimResponse c = file("cust1", product("cust1", "N-1", 2, 12));
        loginAs("agent", Role.SERVICE_AGENT);
        claimService.approve(c.id(), "agent");
        claimService.startRepair(c.id());
        assertEquals(null, claimService.complete(c.id(), "  ").resolutionNote());
    }

    @Test
    void rejectionNeedsAReason() {
        ClaimResponse c = file("cust1", product("cust1", "J-1", 2, 12));
        loginAs("agent", Role.SERVICE_AGENT);
        assertEquals("reason", assertThrows(FieldValidationException.class, () -> claimService.reject(c.id(), "agent", " ")).getField());
        ClaimResponse r = claimService.reject(c.id(), "agent", " Physical damage ");
        assertEquals("REJECTED", r.status());
        assertEquals("Physical damage", r.rejectionReason());
    }

    @Test
    void stepsMustFollowTheWorkflow() {
        ClaimResponse c = file("cust1", product("cust1", "O-1", 2, 12));
        loginAs("agent", Role.SERVICE_AGENT);
        assertThrows(InvalidStatusException.class, () -> claimService.startRepair(c.id())); // not approved yet
        assertThrows(InvalidStatusException.class, () -> claimService.complete(c.id(), null)); // not in repair
        claimService.approve(c.id(), "agent");
        assertThrows(InvalidStatusException.class, () -> claimService.approve(c.id(), "agent2")); // already approved
        assertThrows(InvalidStatusException.class, () -> claimService.reject(c.id(), "agent2", "Late")); // approved claims cannot be rejected
        assertThrows(InvalidStatusException.class, () -> claimService.complete(c.id(), null)); // still not in repair
        claimService.startRepair(c.id());
        claimService.complete(c.id(), null);
        assertThrows(InvalidStatusException.class, () -> claimService.startRepair(c.id())); // finished
        assertThrows(ResourceNotFoundException.class, () -> claimService.approve(999_999L, "agent"));
    }

    // ----- lists -----

    @Test
    void customersSeeOnlyTheirOwnClaims() {
        file("cust1", product("cust1", "L-1", 2, 12));
        file("cust2", product("cust2", "L-2", 2, 12));
        loginAs("cust1", Role.CUSTOMER);
        List<ClaimResponse> mine = claimService.myClaims("cust1", null);
        assertEquals(1, mine.size());
        assertEquals("cust1", mine.get(0).customerUsername());
        assertEquals(0, claimService.myClaims("cust1", ClaimStatus.COMPLETED).size());
    }

    @Test
    void pendingQueueShowsHighPriorityFirstThenOldest() {
        Product a = product("cust1", "Q-1", 2, 12);
        Product b = product("cust1", "Q-2", 2, 12);
        Product c = product("cust2", "Q-3", 2, 12);
        loginAs("cust1", Role.CUSTOMER);
        claimService.file("cust1", new ClaimRequest(a.getId(), "low one", "LOW"));
        claimService.file("cust1", new ClaimRequest(b.getId(), "high one", "HIGH"));
        loginAs("cust2", Role.CUSTOMER);
        claimService.file("cust2", new ClaimRequest(c.getId(), "medium one", "MEDIUM"));

        loginAs("agent", Role.SERVICE_AGENT);
        List<String> order = claimService.list(ClaimStatus.PENDING, null, null).stream().map(ClaimResponse::issueDescription).toList();
        assertEquals(List.of("high one", "medium one", "low one"), order);
        assertEquals(1, claimService.list(null, "high", null).size());
        assertEquals(1, claimService.list(null, null, "customer two").size());
        assertEquals(3, claimService.list(null, null, "acme").size());
        assertEquals(3, claimService.summary().stream().filter(s -> s.status().equals("PENDING")).findFirst().orElseThrow().count());
    }

    // ----- concurrency -----

    @Test
    void doubleClickingFileClaimCreatesOnlyOneActiveClaim() throws Exception {
        Product p = product("cust1", "X-1", 2, 12);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("cust1", Role.CUSTOMER);
                try {
                    claimService.file("cust1", new ClaimRequest(p.getId(), "Race", null));
                    return true;
                } catch (ConflictException ex) {
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
            assertEquals(1, claims.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoAgentsCannotDecideTheSameClaimAtOnce() throws Exception {
        ClaimResponse c = file("cust1", product("cust1", "Y-1", 2, 12));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> () -> {
                String agent = i % 2 == 0 ? "agent" : "agent2";
                loginAs(agent, Role.SERVICE_AGENT);
                try {
                    if (i < 2) {
                        claimService.approve(c.id(), agent);
                    } else {
                        claimService.reject(c.id(), agent, "No");
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
}
