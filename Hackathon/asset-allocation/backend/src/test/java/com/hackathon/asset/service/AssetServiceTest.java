package com.hackathon.asset.service;

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

import com.hackathon.asset.dto.AcceptBody;
import com.hackathon.asset.dto.AssetBody;
import com.hackathon.asset.dto.AssetDashboard;
import com.hackathon.asset.dto.AssetResponse;
import com.hackathon.asset.dto.AssignBody;
import com.hackathon.asset.dto.AssignmentResponse;
import com.hackathon.asset.dto.EmployeeResponse;
import com.hackathon.asset.entity.Asset;
import com.hackathon.asset.entity.AssetCategory;
import com.hackathon.asset.entity.AssetCondition;
import com.hackathon.asset.entity.AssetStatus;
import com.hackathon.asset.entity.Assignment;
import com.hackathon.asset.entity.AssignmentStatus;
import com.hackathon.asset.entity.Role;
import com.hackathon.asset.entity.User;
import com.hackathon.asset.exception.BusinessRuleException;
import com.hackathon.asset.exception.FieldValidationException;
import com.hackathon.asset.exception.ForbiddenActionException;
import com.hackathon.asset.exception.InvalidStatusException;
import com.hackathon.asset.exception.ResourceNotFoundException;
import com.hackathon.asset.repo.AssetRepository;
import com.hackathon.asset.repo.AssignmentRepository;
import com.hackathon.asset.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class AssetServiceTest {

    @Autowired AssetService service;
    @Autowired AssetRepository assets;
    @Autowired AssignmentRepository assignments;
    @Autowired UserRepository users;

    Asset laptop;
    Asset monitor;
    Asset phone;
    Asset mouse;

    @BeforeEach
    void setUp() {
        assignments.deleteAll();
        assets.deleteAll();
        users.deleteAll();
        users.save(new User("admin", "x", "Asset Admin", Role.ASSET_ADMIN));
        for (int i = 1; i <= 6; i++) {
            users.save(new User("e" + i, "x", "Employee " + i, Role.EMPLOYEE));
        }
        laptop = asset("LT-1001", "LT00001111", "Dell Latitude", AssetCategory.LAPTOP, "Dell");
        monitor = asset("MN-1001", "MN00002222", "LG 27 inch", AssetCategory.MONITOR, "LG");
        phone = asset("PH-1001", "PH00003333", "iPhone 14", AssetCategory.PHONE, "Apple");
        mouse = asset("AC-1001", "AC00004444", "Logitech mouse", AssetCategory.ACCESSORY, "Logitech");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    Asset asset(String tag, String serial, String name, AssetCategory category, String brand) {
        return assets.save(new Asset(tag, serial, name, category, brand, null, LocalDate.now().minusDays(100), new BigDecimal("50000.00"), AssetCondition.GOOD, null));
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    AssignmentResponse assign(Asset a, String employee) {
        loginAs("admin", Role.ASSET_ADMIN);
        return service.assign("admin", a.getId(), new AssignBody(employee, null, null));
    }

    AssignmentResponse askToReturn(AssignmentResponse a) {
        loginAs(a.employeeUsername(), Role.EMPLOYEE);
        return service.requestReturn(a.employeeUsername(), a.id(), "Done with it");
    }

    AssignmentResponse accept(AssignmentResponse a, AssetCondition condition) {
        loginAs("admin", Role.ASSET_ADMIN);
        return service.accept("admin", a.id(), new AcceptBody(condition, "Checked"));
    }

    AssetBody body(String tag, String serial) {
        return new AssetBody(tag, serial, "Dell Monitor", AssetCategory.MONITOR, "Dell", "U2723", LocalDate.now().minusDays(5), new BigDecimal("30000"), AssetCondition.NEW, null);
    }

    // ---------- the register ----------

    @Test
    void creatingAnAssetTidiesTheTagAndSerial() {
        loginAs("admin", Role.ASSET_ADMIN);
        AssetResponse a = service.createAsset(body(" mn-2001 ", "ab-12345"));
        assertEquals("MN-2001", a.assetTag());
        assertEquals("AB-12345", a.serialNumber());
        assertEquals("AVAILABLE", a.status());
        assertEquals("NEW", a.condition());
    }

    @Test
    void tagsAndSerialNumbersAreUniqueInAnyCase() {
        loginAs("admin", Role.ASSET_ADMIN);
        FieldValidationException tag = assertThrows(FieldValidationException.class, () -> service.createAsset(body("lt-1001", "SOMETHING1")));
        assertEquals("assetTag", tag.getField());
        FieldValidationException serial = assertThrows(FieldValidationException.class, () -> service.createAsset(body("LT-2002", "lt00001111")));
        assertEquals("serialNumber", serial.getField());
    }

    @Test
    void aPurchaseDateInTheFutureIsRejected() {
        loginAs("admin", Role.ASSET_ADMIN);
        AssetBody future = new AssetBody("LT-3001", "FUTURE0001", "Laptop", AssetCategory.LAPTOP, null, null, LocalDate.now().plusDays(1), null, AssetCondition.NEW, null);
        assertEquals("purchaseDate", assertThrows(FieldValidationException.class, () -> service.createAsset(future)).getField());
    }

    @Test
    void editingKeepsUniquenessAndRetiredAssetsAreReadOnly() {
        loginAs("admin", Role.ASSET_ADMIN);
        AssetResponse same = service.updateAsset(laptop.getId(), new AssetBody("LT-1001", "LT00001111", "Dell Latitude 5440", AssetCategory.LAPTOP, "Dell", "5440", null, null, AssetCondition.FAIR, "Dented"));
        assertEquals("Dell Latitude 5440", same.name());
        assertEquals("FAIR", same.condition());
        assertThrows(FieldValidationException.class, () -> service.updateAsset(laptop.getId(), body("MN-1001", "LT00001111")));
        service.changeStatus(mouse.getId(), AssetStatus.RETIRED);
        assertThrows(BusinessRuleException.class, () -> service.updateAsset(mouse.getId(), body("AC-1001", "AC00004444")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateAsset(999999L, body("XX-9999", "XXXXXXXX")));
    }

    @Test
    void searchFindsBySerialTagNameAndHolderAndFilters() {
        assign(laptop, "e1");
        loginAs("admin", Role.ASSET_ADMIN);
        assertEquals(4, service.assets(null, null, null).size());
        assertEquals(List.of("MN-1001"), service.assets(null, null, "mn00002222").stream().map(AssetResponse::assetTag).toList());
        assertEquals(List.of("MN-1001"), service.assets(null, null, "mn 1001").stream().map(AssetResponse::assetTag).toList());
        assertEquals(List.of("LT-1001"), service.assets(null, null, "employee 1").stream().map(AssetResponse::assetTag).toList());
        assertEquals(1, service.assets(null, null, "apple").size());
        assertEquals(1, service.assets(AssetStatus.ASSIGNED, null, null).size());
        assertEquals(3, service.assets(AssetStatus.AVAILABLE, null, null).size());
        assertEquals(1, service.assets(null, AssetCategory.PHONE, null).size());
        assertEquals("Employee 1", service.asset(laptop.getId()).holderName());
    }

    // ---------- assigning ----------

    @Test
    void assigningMakesTheAssetAssignedAndRecordsTheHandOver() {
        AssignmentResponse a = assign(laptop, "e1");
        assertEquals("ASSIGNED", a.status());
        assertEquals("admin", a.assignedBy());
        assertEquals("GOOD", a.conditionOut());
        assertTrue(a.canRequestReturn());
        assertEquals("ASSIGNED", assets.findById(laptop.getId()).orElseThrow().getStatus().name());
    }

    @Test
    void onlyAvailableAssetsCanBeAssigned() {
        assign(laptop, "e1");
        InvalidStatusException held = assertThrows(InvalidStatusException.class, () -> assign(laptop, "e2"));
        assertTrue(held.getMessage().contains("Employee 1"));
        loginAs("admin", Role.ASSET_ADMIN);
        service.changeStatus(phone.getId(), AssetStatus.IN_REPAIR);
        assertThrows(InvalidStatusException.class, () -> assign(phone, "e2"));
        service.changeStatus(mouse.getId(), AssetStatus.RETIRED);
        assertThrows(InvalidStatusException.class, () -> assign(mouse, "e2"));
    }

    @Test
    void theEmployeeAndDueDateAreChecked() {
        loginAs("admin", Role.ASSET_ADMIN);
        assertEquals("employeeUsername", assertThrows(FieldValidationException.class, () -> service.assign("admin", laptop.getId(), new AssignBody("nobody", null, null))).getField());
        assertEquals("employeeUsername", assertThrows(FieldValidationException.class, () -> service.assign("admin", laptop.getId(), new AssignBody("admin", null, null))).getField());
        assertEquals("dueDate", assertThrows(FieldValidationException.class, () -> service.assign("admin", laptop.getId(), new AssignBody("e1", LocalDate.now(), null))).getField());
        AssignmentResponse ok = service.assign("admin", laptop.getId(), new AssignBody("e1", LocalDate.now().plusDays(30), " For the project "));
        assertEquals(LocalDate.now().plusDays(30), ok.dueDate());
        assertEquals("For the project", ok.note());
        assertFalse(ok.overdue());
    }

    @Test
    void nobodyCanHoldMoreThanFiveAssets() {
        for (int i = 1; i <= 5; i++) {
            assign(asset("AC-20" + i, "ACCSERIAL" + i, "Item " + i, AssetCategory.ACCESSORY, null), "e1");
        }
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> assign(laptop, "e1"));
        assertTrue(e.getMessage().contains("5 assets"));
        assertEquals("ASSIGNED", assign(laptop, "e2").status());
    }

    @Test
    void rolesAreEnforcedOnTheServiceMethods() {
        AssignmentResponse a = assign(laptop, "e1");
        loginAs("e1", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> service.assets(null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.assign("e1", monitor.getId(), new AssignBody("e1", null, null)));
        assertThrows(AccessDeniedException.class, () -> service.accept("e1", a.id(), new AcceptBody(AssetCondition.GOOD, null)));
        assertThrows(AccessDeniedException.class, () -> service.dashboard());
        loginAs("admin", Role.ASSET_ADMIN);
        assertThrows(AccessDeniedException.class, () -> service.myAssets("admin"));
        assertThrows(AccessDeniedException.class, () -> service.requestReturn("admin", a.id(), null));
    }

    // ---------- returning ----------

    @Test
    void anEmployeeSeesTheirAssetsAndAsksToReturnOne() {
        AssignmentResponse a = assign(laptop, "e1");
        assign(monitor, "e1");
        assign(phone, "e2");
        loginAs("e1", Role.EMPLOYEE);
        assertEquals(2, service.myAssets("e1").size());
        assertEquals(0, service.myHistory("e1").size());
        AssignmentResponse asked = service.requestReturn("e1", a.id(), "  Upgrading  ");
        assertEquals("RETURN_REQUESTED", asked.status());
        assertEquals("Upgrading", asked.returnNote());
        assertFalse(asked.canRequestReturn());
        assertEquals(AssetStatus.RETURN_REQUESTED, assets.findById(laptop.getId()).orElseThrow().getStatus());
        assertEquals(2, service.myAssets("e1").size());
    }

    @Test
    void onlyTheHolderAsksAndOnlyOnce() {
        AssignmentResponse a = assign(laptop, "e1");
        loginAs("e2", Role.EMPLOYEE);
        assertThrows(ForbiddenActionException.class, () -> service.requestReturn("e2", a.id(), null));
        askToReturn(a);
        loginAs("e1", Role.EMPLOYEE);
        assertThrows(InvalidStatusException.class, () -> service.requestReturn("e1", a.id(), null));
        assertThrows(ResourceNotFoundException.class, () -> service.requestReturn("e1", 999999L, null));
    }

    @Test
    void acceptingTheReturnMakesTheAssetAvailableAgain() {
        AssignmentResponse a = assign(laptop, "e1");
        loginAs("admin", Role.ASSET_ADMIN);
        assertThrows(InvalidStatusException.class, () -> service.accept("admin", a.id(), new AcceptBody(AssetCondition.GOOD, null)));
        askToReturn(a);
        AssignmentResponse done = accept(a, AssetCondition.FAIR);
        assertEquals("RETURNED", done.status());
        assertEquals("FAIR", done.conditionIn());
        assertEquals("admin", done.receivedBy());
        Asset back = assets.findById(laptop.getId()).orElseThrow();
        assertEquals(AssetStatus.AVAILABLE, back.getStatus());
        assertEquals(AssetCondition.FAIR, back.getCondition());
        assertThrows(InvalidStatusException.class, () -> accept(a, AssetCondition.GOOD));
        loginAs("e1", Role.EMPLOYEE);
        assertEquals(0, service.myAssets("e1").size());
        assertEquals(1, service.myHistory("e1").size());
        assertThrows(InvalidStatusException.class, () -> service.requestReturn("e1", a.id(), null));
    }

    @Test
    void anAssetCanBeHandedOnAndItsHistoryKeepsEveryHolder() {
        accept(askToReturn(assign(laptop, "e1")), AssetCondition.GOOD);
        accept(askToReturn(assign(laptop, "e2")), AssetCondition.POOR);
        assign(laptop, "e3");
        loginAs("admin", Role.ASSET_ADMIN);
        List<AssignmentResponse> history = service.history(laptop.getId());
        assertEquals(List.of("e3", "e2", "e1"), history.stream().map(AssignmentResponse::employeeUsername).toList());
        assertEquals(List.of("ASSIGNED", "RETURNED", "RETURNED"), history.stream().map(AssignmentResponse::status).toList());
        assertEquals("POOR", history.get(1).conditionIn());
        assertEquals("POOR", history.get(0).conditionOut());
        assertThrows(ResourceNotFoundException.class, () -> service.history(999999L));
    }

    // ---------- status changes ----------

    @Test
    void repairAndRetirementFollowTheAllowedMoves() {
        loginAs("admin", Role.ASSET_ADMIN);
        assertEquals("IN_REPAIR", service.changeStatus(phone.getId(), AssetStatus.IN_REPAIR).status());
        assertThrows(InvalidStatusException.class, () -> service.changeStatus(phone.getId(), AssetStatus.IN_REPAIR));
        assertThrows(InvalidStatusException.class, () -> service.changeStatus(phone.getId(), AssetStatus.ASSIGNED));
        assertEquals("AVAILABLE", service.changeStatus(phone.getId(), AssetStatus.AVAILABLE).status());
        assertEquals("RETIRED", service.changeStatus(phone.getId(), AssetStatus.RETIRED).status());
        assertThrows(InvalidStatusException.class, () -> service.changeStatus(phone.getId(), AssetStatus.AVAILABLE));
    }

    @Test
    void anAssetWithAnEmployeeCannotBeMovedUntilItIsBack() {
        AssignmentResponse a = assign(laptop, "e1");
        loginAs("admin", Role.ASSET_ADMIN);
        assertThrows(BusinessRuleException.class, () -> service.changeStatus(laptop.getId(), AssetStatus.IN_REPAIR));
        askToReturn(a);
        loginAs("admin", Role.ASSET_ADMIN);
        assertThrows(BusinessRuleException.class, () -> service.changeStatus(laptop.getId(), AssetStatus.RETIRED));
    }

    // ---------- lists and dashboard ----------

    @Test
    void theReturnQueueComesFirstAndOldestRequestLeads() {
        AssignmentResponse first = assign(laptop, "e1");
        AssignmentResponse second = assign(monitor, "e2");
        assign(phone, "e3");
        askToReturn(second);
        askToReturn(first);
        loginAs("admin", Role.ASSET_ADMIN);
        List<AssignmentResponse> open = service.openAssignments(null);
        assertEquals(3, open.size());
        assertEquals(List.of(second.id(), first.id()), open.subList(0, 2).stream().map(AssignmentResponse::id).toList());
        assertEquals("ASSIGNED", open.get(2).status());
        assertEquals(2, service.openAssignments(AssignmentStatus.RETURN_REQUESTED).size());
    }

    @Test
    void employeesAreListedWithTheNumberOfAssetsTheyHold() {
        assign(laptop, "e1");
        assign(monitor, "e1");
        loginAs("admin", Role.ASSET_ADMIN);
        List<EmployeeResponse> all = service.employees(null);
        assertEquals(6, all.size());
        assertEquals(2, all.stream().filter(e -> e.username().equals("e1")).findFirst().orElseThrow().activeAssets());
        assertEquals(List.of("e3"), service.employees("employee 3").stream().map(EmployeeResponse::username).toList());
    }

    @Test
    void theDashboardCountsEverything() {
        Asset late = asset("LT-1002", "LT00005555", "Old laptop", AssetCategory.LAPTOP, "HP");
        assign(laptop, "e1");
        askToReturn(assign(monitor, "e2"));
        Assignment overdue = assignments.save(new Assignment(late, users.findByUsername("e3").orElseThrow(), "admin", LocalDate.now().minusDays(3), null));
        late.setStatus(AssetStatus.ASSIGNED);
        assets.save(late);
        loginAs("admin", Role.ASSET_ADMIN);
        service.changeStatus(phone.getId(), AssetStatus.IN_REPAIR);
        service.changeStatus(mouse.getId(), AssetStatus.RETIRED);
        AssetDashboard d = service.dashboard();
        assertEquals(5, d.totalAssets());
        assertEquals(0, d.available());
        assertEquals(3, d.assigned());
        assertEquals(1, d.returnRequests());
        assertEquals(1, d.inRepair());
        assertEquals(1, d.retired());
        assertEquals(1, d.overdue());
        assertEquals(0, d.poorCondition());
        assertEquals(new BigDecimal("200000.00"), d.assetValue());
        assertEquals("LAPTOP", d.byCategory().get(0).key());
        assertEquals(2L, d.byCategory().get(0).count());
        assertEquals(1, d.oldestRequests().size());
        assertTrue(overdue.getId() != null);
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
    void anAssetHandedToFiveEmployeesAtOnceGoesToOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.rangeClosed(1, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("admin", Role.ASSET_ADMIN);
                try {
                    service.assign("admin", laptop.getId(), new AssignBody("e" + i, null, null));
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, assignments.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void oneEmployeeCannotGetMoreThanFiveAssetsEvenWhenTheyAreAssignedTogether() throws Exception {
        List<Asset> items = IntStream.range(0, 7).mapToObj(i -> asset("AC-30" + i, "RACESERIAL" + i, "Item " + i, AssetCategory.ACCESSORY, null)).toList();
        ExecutorService pool = Executors.newFixedThreadPool(7);
        try {
            List<Callable<Boolean>> tasks = items.stream().<Callable<Boolean>>map(item -> () -> {
                loginAs("admin", Role.ASSET_ADMIN);
                try {
                    service.assign("admin", item.getId(), new AssignBody("e1", null, null));
                    return true;
                } catch (BusinessRuleException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(5, winners(pool.invokeAll(tasks)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aDoubleClickedReturnRequestIsRecordedOnce() throws Exception {
        AssignmentResponse a = assign(laptop, "e1");
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 3).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("e1", Role.EMPLOYEE);
                try {
                    service.requestReturn("e1", a.id(), null);
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(AssignmentStatus.RETURN_REQUESTED, assignments.findById(a.id()).orElseThrow().getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoAdminsAcceptingTheSameReturnMakeOneRecord() throws Exception {
        AssignmentResponse a = askToReturn(assign(laptop, "e1"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 2).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("admin", Role.ASSET_ADMIN);
                try {
                    service.accept("admin", a.id(), new AcceptBody(AssetCondition.GOOD, null));
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(AssetStatus.AVAILABLE, assets.findById(laptop.getId()).orElseThrow().getStatus());
        } finally {
            pool.shutdownNow();
        }
    }
}
