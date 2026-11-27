package com.hackathon.leave.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
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

import com.hackathon.leave.dto.BalanceResponse;
import com.hackathon.leave.dto.LeaveResponse;
import com.hackathon.leave.dto.LeaveSubmission;
import com.hackathon.leave.entity.LeaveBalance;
import com.hackathon.leave.entity.LeaveRequest;
import com.hackathon.leave.entity.LeaveStatus;
import com.hackathon.leave.entity.LeaveType;
import com.hackathon.leave.entity.Role;
import com.hackathon.leave.entity.User;
import com.hackathon.leave.exception.BusinessRuleException;
import com.hackathon.leave.exception.ConflictException;
import com.hackathon.leave.exception.FieldValidationException;
import com.hackathon.leave.exception.ForbiddenActionException;
import com.hackathon.leave.exception.InvalidStatusException;
import com.hackathon.leave.exception.ResourceNotFoundException;
import com.hackathon.leave.repo.LeaveBalanceRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class LeaveServiceTest {

    @Autowired LeaveService service;
    @Autowired LeaveRequestRepository requests;
    @Autowired LeaveBalanceRepository balances;
    @Autowired UserRepository users;

    /** A Monday in March of the current year: Mon..Fri are M..M+4, the weekend is M+5 and M+6. */
    static final LocalDate M = LocalDate.of(LocalDate.now().getYear(), Month.MARCH, 1)
            .with(TemporalAdjusters.firstInMonth(java.time.DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        requests.deleteAll();
        balances.deleteAll();
        users.deleteAll();
        users.save(new User("mgr", "x", "Manager One", Role.MANAGER));
        users.save(new User("mgr2", "x", "Manager Two", Role.MANAGER));
        users.save(new User("emp1", "x", "Employee One", Role.EMPLOYEE));
        users.save(new User("emp2", "x", "Employee Two", Role.EMPLOYEE));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    private LeaveResponse submit(String who, String type, LocalDate from, LocalDate to) {
        loginAs(who, who.startsWith("mgr") ? Role.MANAGER : Role.EMPLOYEE);
        return service.submit(who, new LeaveSubmission(type, from, to, "Time off"));
    }

    private BalanceResponse balance(String who, String type) {
        loginAs(who, who.startsWith("mgr") ? Role.MANAGER : Role.EMPLOYEE);
        return service.myBalances(who, null).stream().filter(b -> b.leaveType().equals(type)).findFirst().orElseThrow();
    }

    // ----- working days -----

    @Test
    void workingDaysSkipWeekends() {
        assertEquals(5, LeaveService.workingDays(M, M.plusDays(4)));
        assertEquals(2, LeaveService.workingDays(M.plusDays(4), M.plusDays(7))); // Friday + Monday
        assertEquals(0, LeaveService.workingDays(M.plusDays(5), M.plusDays(6))); // weekend only
        assertEquals(1, LeaveService.workingDays(M, M));
    }

    // ----- validation -----

    @Test
    void endBeforeStartIsRejected() {
        assertEquals("endDate", assertThrows(FieldValidationException.class,
                () -> submit("emp1", "ANNUAL", M.plusDays(2), M)).getField());
    }

    @Test
    void weekendOnlyRangeIsRejected() {
        assertEquals("startDate", assertThrows(FieldValidationException.class,
                () -> submit("emp1", "ANNUAL", M.plusDays(5), M.plusDays(6))).getField());
    }

    @Test
    void leaveCannotCrossIntoAnotherYear() {
        int year = M.getYear();
        assertEquals("endDate", assertThrows(FieldValidationException.class,
                () -> submit("emp1", "ANNUAL", LocalDate.of(year, 12, 30), LocalDate.of(year + 1, 1, 2))).getField());
    }

    @Test
    void unknownLeaveTypeIsRejected() {
        assertEquals("leaveType", assertThrows(FieldValidationException.class,
                () -> submit("emp1", "SABBATICAL", M, M)).getField());
    }

    // ----- balances -----

    @Test
    void submittingReservesDaysWithoutUsingThem() {
        LeaveResponse r = submit("emp1", "ANNUAL", M, M.plusDays(2));
        assertEquals("PENDING", r.status());
        assertEquals(3, r.days());
        BalanceResponse b = balance("emp1", "ANNUAL");
        assertEquals(18, b.allocated());
        assertEquals(0, b.used());
        assertEquals(3, b.pending());
        assertEquals(15, b.available());
    }

    @Test
    void cannotRequestMoreThanTheBalance() {
        // casual leave allows 6 days; a full 2-week range has 10 working days
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> submit("emp1", "CASUAL", M, M.plusDays(11)));
        assertTrue(ex.getMessage().contains("Not enough"));
    }

    @Test
    void pendingRequestsCountAgainstTheBalance() {
        submit("emp1", "CASUAL", M, M.plusDays(4)); // 5 of 6 days reserved
        assertThrows(BusinessRuleException.class, () -> submit("emp1", "CASUAL", M.plusDays(14), M.plusDays(15)));
    }

    @Test
    void unpaidLeaveIsNotLimited() {
        LeaveResponse r = submit("emp1", "UNPAID", M, M.plusDays(11));
        assertEquals(10, r.days());
    }

    @Test
    void balancesCanBeViewedForNextYearButNotForDistantYears() {
        loginAs("emp1", Role.EMPLOYEE);
        int year = LocalDate.now().getYear();
        assertEquals(4, service.myBalances("emp1", year + 1).size());
        assertEquals("year", assertThrows(FieldValidationException.class,
                () -> service.myBalances("emp1", year + 5)).getField());
    }

    // ----- overlap -----

    @Test
    void overlappingPendingOrApprovedLeaveIsRejected() {
        LeaveResponse first = submit("emp1", "ANNUAL", M, M.plusDays(2));
        assertThrows(ConflictException.class, () -> submit("emp1", "SICK", M.plusDays(2), M.plusDays(4)));

        loginAs("mgr", Role.MANAGER);
        service.approve(first.id(), "mgr", null);
        assertThrows(ConflictException.class, () -> submit("emp1", "CASUAL", M, M));
    }

    @Test
    void cancelledLeaveNoLongerBlocksTheDates() {
        LeaveResponse first = submit("emp1", "ANNUAL", M, M.plusDays(2));
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals("CANCELLED", service.cancel("emp1", first.id()).status());
        assertEquals("PENDING", submit("emp1", "ANNUAL", M, M.plusDays(2)).status());
    }

    @Test
    void otherEmployeesDoNotBlockEachOther() {
        submit("emp1", "ANNUAL", M, M.plusDays(2));
        assertEquals("PENDING", submit("emp2", "ANNUAL", M, M.plusDays(2)).status());
    }

    // ----- approval -----

    @Test
    void approvingDeductsTheBalanceAndRecordsTheDecision() {
        LeaveResponse r = submit("emp1", "ANNUAL", M, M.plusDays(2));
        loginAs("mgr", Role.MANAGER);
        LeaveResponse approved = service.approve(r.id(), "mgr", "Enjoy");
        assertEquals("APPROVED", approved.status());
        assertEquals("mgr", approved.decidedBy());
        assertEquals("Enjoy", approved.decisionNote());

        BalanceResponse b = balance("emp1", "ANNUAL");
        assertEquals(3, b.used());
        assertEquals(0, b.pending());
        assertEquals(15, b.available());
    }

    @Test
    void rejectingNeedsNoReasonAndKeepsTheBalance() {
        LeaveResponse r = submit("emp1", "ANNUAL", M, M.plusDays(2));
        loginAs("mgr", Role.MANAGER);
        assertEquals("REJECTED", service.reject(r.id(), "mgr", null).status());
        BalanceResponse b = balance("emp1", "ANNUAL");
        assertEquals(0, b.used());
        assertEquals(18, b.available());
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        LeaveResponse r = submit("emp1", "ANNUAL", M, M);
        loginAs("mgr", Role.MANAGER);
        service.approve(r.id(), "mgr", null);
        assertThrows(InvalidStatusException.class, () -> service.approve(r.id(), "mgr2", null));
        assertThrows(InvalidStatusException.class, () -> service.reject(r.id(), "mgr2", null));
    }

    @Test
    void aManagerCannotDecideTheirOwnLeaveButAnotherManagerCan() {
        LeaveResponse own = submit("mgr", "ANNUAL", M, M.plusDays(1));
        loginAs("mgr", Role.MANAGER);
        assertThrows(ForbiddenActionException.class, () -> service.approve(own.id(), "mgr", null));
        assertThrows(ForbiddenActionException.class, () -> service.reject(own.id(), "mgr", null));

        loginAs("mgr2", Role.MANAGER);
        assertEquals("APPROVED", service.approve(own.id(), "mgr2", null).status());
    }

    @Test
    void employeesCannotDecideOrListEverything() {
        LeaveResponse r = submit("emp1", "ANNUAL", M, M);
        loginAs("emp2", Role.EMPLOYEE);
        assertThrows(AccessDeniedException.class, () -> service.approve(r.id(), "emp2", null));
        assertThrows(AccessDeniedException.class, () -> service.allLeave(null, null, null));
        assertThrows(AccessDeniedException.class, () -> service.calendar(YearMonth.from(M)));
    }

    @Test
    void approvalIsBlockedWhenItWouldOverlapApprovedLeave() {
        // Two pending requests for the same dates cannot be created through submit, so plant the second directly.
        LeaveResponse first = submit("emp1", "ANNUAL", M, M.plusDays(2));
        loginAs("mgr", Role.MANAGER);
        service.approve(first.id(), "mgr", null);
        User emp1 = users.findByUsername("emp1").orElseThrow();
        Long planted = requests.save(new LeaveRequest(emp1, LeaveType.SICK, M.plusDays(1), M.plusDays(3), 3, "Planted")).getId();
        assertThrows(ConflictException.class, () -> service.approve(planted, "mgr", null));
    }

    // ----- cancelling -----

    @Test
    void cancellingFutureApprovedLeaveGivesTheDaysBack() {
        User emp1 = users.findByUsername("emp1").orElseThrow();
        loginAs("emp1", Role.EMPLOYEE);
        service.myBalances("emp1", null); // creates this year's balance rows
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LeaveRequest approved = new LeaveRequest(emp1, LeaveType.ANNUAL, tomorrow, tomorrow, 4, "Trip");
        approved.decide(LeaveStatus.APPROVED, "mgr", null);
        Long id = requests.save(approved).getId();
        LeaveBalance annual = balances.findByUserAndLeaveYearOrderByLeaveType(emp1, LocalDate.now().getYear()).stream()
                .filter(b -> b.getLeaveType() == LeaveType.ANNUAL).findFirst().orElseThrow();
        annual.setUsed(4);
        balances.save(annual);

        assertEquals("CANCELLED", service.cancel("emp1", id).status());
        assertEquals(0, balance("emp1", "ANNUAL").used());
    }

    @Test
    void leaveThatAlreadyStartedCannotBeCancelled() {
        User emp1 = users.findByUsername("emp1").orElseThrow();
        loginAs("emp1", Role.EMPLOYEE);
        service.myBalances("emp1", null);
        LeaveRequest started = new LeaveRequest(emp1, LeaveType.ANNUAL, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1), 2, "Trip");
        started.decide(LeaveStatus.APPROVED, "mgr", null);
        Long id = requests.save(started).getId();
        assertThrows(BusinessRuleException.class, () -> service.cancel("emp1", id));
    }

    @Test
    void youCannotCancelSomeoneElsesRequestOrADecidedOne() {
        LeaveResponse mine = submit("emp1", "ANNUAL", M, M);
        loginAs("emp2", Role.EMPLOYEE);
        assertThrows(ResourceNotFoundException.class, () -> service.cancel("emp2", mine.id()));

        loginAs("mgr", Role.MANAGER);
        service.reject(mine.id(), "mgr", "No");
        loginAs("emp1", Role.EMPLOYEE);
        assertThrows(InvalidStatusException.class, () -> service.cancel("emp1", mine.id()));
    }

    // ----- visibility and calendar -----

    @Test
    void employeesSeeOnlyTheirOwnLeaveAndManagersSeeAll() {
        submit("emp1", "ANNUAL", M, M);
        submit("emp2", "ANNUAL", M.plusDays(7), M.plusDays(7));
        loginAs("emp1", Role.EMPLOYEE);
        assertEquals(1, service.myLeave("emp1", null, null).size());
        loginAs("mgr", Role.MANAGER);
        assertEquals(2, service.allLeave(null, null, null).size());
        assertEquals(1, service.allLeave(null, null, "employee two").size());
        assertEquals(0, service.allLeave(LeaveStatus.APPROVED, null, null).size());
    }

    @Test
    void calendarShowsOnlyApprovedLeaveInTheMonth() {
        LeaveResponse approved = submit("emp1", "ANNUAL", M, M.plusDays(1));
        submit("emp2", "ANNUAL", M, M.plusDays(1)); // stays pending
        loginAs("mgr", Role.MANAGER);
        service.approve(approved.id(), "mgr", null);
        List<LeaveResponse> march = service.calendar(YearMonth.from(M));
        assertEquals(1, march.size());
        assertEquals("emp1", march.get(0).employeeUsername());
        assertEquals(0, service.calendar(YearMonth.from(M).plusMonths(2)).size());
    }

    // ----- concurrency -----

    @Test
    void twoManagersCannotDecideTheSameRequestAtOnce() throws Exception {
        LeaveResponse r = submit("emp1", "ANNUAL", M, M.plusDays(2));
        long winners = runInParallel(4, i -> {
            String manager = i % 2 == 0 ? "mgr" : "mgr2";
            loginAs(manager, Role.MANAGER);
            try {
                if (i < 2) {
                    service.approve(r.id(), manager, null);
                } else {
                    service.reject(r.id(), manager, "No");
                }
                return true;
            } catch (InvalidStatusException ex) {
                return false;
            }
        });
        assertEquals(1, winners);
    }

    @Test
    void overlappingSubmissionsRacingEachOtherLetOnlyOneThrough() throws Exception {
        loginAs("emp1", Role.EMPLOYEE);
        service.myBalances("emp1", null); // rows exist, so the threads only compete on the leave itself
        long winners = runInParallel(5, i -> {
            loginAs("emp1", Role.EMPLOYEE);
            try {
                service.submit("emp1", new LeaveSubmission("ANNUAL", M.plusDays(i % 3), M.plusDays(2 + i % 3), "Race"));
                return true;
            } catch (ConflictException ex) {
                return false;
            }
        });
        assertEquals(1, winners);
        assertEquals(1, requests.findAll().size());
    }

    private long runInParallel(int threads, java.util.function.IntFunction<Boolean> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, threads).<Callable<Boolean>>mapToObj(i -> () -> {
                try {
                    return task.apply(i);
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
            return winners;
        } finally {
            pool.shutdownNow();
        }
    }
}
