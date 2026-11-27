package com.hackathon.leave.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.leave.dto.BalanceResponse;
import com.hackathon.leave.dto.LeaveResponse;
import com.hackathon.leave.dto.LeaveSubmission;
import com.hackathon.leave.dto.TypeOption;
import com.hackathon.leave.entity.LeaveBalance;
import com.hackathon.leave.entity.LeaveRequest;
import com.hackathon.leave.entity.LeaveStatus;
import com.hackathon.leave.entity.LeaveType;
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

/**
 * All leave rules. Every operation that depends on one employee's leave first takes a lock on that employee's
 * balance rows (then on the request row, always in that order), so concurrent submissions, approvals and
 * cancellations for the same person are handled one at a time and cannot overlap or overspend.
 */
@Service
public class LeaveService {

    private static final String ANY_STAFF = "hasAnyRole('EMPLOYEE','MANAGER')";
    private static final EnumSet<LeaveStatus> ACTIVE = EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final UserRepository users;

    public LeaveService(LeaveRequestRepository requests, LeaveBalanceRepository balances, UserRepository users) {
        this.requests = requests;
        this.balances = balances;
        this.users = users;
    }

    // ---------- reference data ----------

    public List<TypeOption> types() {
        return Arrays.stream(LeaveType.values())
                .map(t -> new TypeOption(t.name(), t.getLabel(), t.isTracked()))
                .collect(Collectors.toList());
    }

    /** Monday to Friday days in [start, end], both included. */
    public static int workingDays(LocalDate start, LocalDate end) {
        return (int) start.datesUntil(end.plusDays(1))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();
    }

    // ---------- employee ----------

    @PreAuthorize(ANY_STAFF)
    @Transactional
    public List<BalanceResponse> myBalances(String username, Integer requestedYear) {
        User user = findUser(username);
        int thisYear = LocalDate.now().getYear();
        int year = requestedYear == null ? thisYear : requestedYear;
        if (year < thisYear - 1 || year > thisYear + 1) {
            throw new FieldValidationException("year", "Year must be between " + (thisYear - 1) + " and " + (thisYear + 1));
        }
        List<LeaveBalance> rows = ensureBalances(user, year);
        List<LeaveRequest> pending = requests.findPendingInYear(user, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        return rows.stream().map(b -> {
            int reserved = pendingDays(pending, b.getLeaveType());
            int available = b.getLeaveType().isTracked() ? b.getAllocated() - b.getUsed() - reserved : 0;
            return new BalanceResponse(b.getLeaveType().name(), b.getLeaveType().getLabel(),
                    b.getLeaveType().isTracked(), b.getAllocated(), b.getUsed(), reserved, available);
        }).collect(Collectors.toList());
    }

    @PreAuthorize(ANY_STAFF)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaveResponse submit(String username, LeaveSubmission dto) {
        User user = findUser(username);
        LeaveType type = parseType(dto.leaveType());
        LocalDate start = dto.startDate();
        LocalDate end = dto.endDate();
        if (end.isBefore(start)) {
            throw new FieldValidationException("endDate", "End date cannot be before the start date");
        }
        if (start.getYear() != end.getYear()) {
            throw new FieldValidationException("endDate",
                    "Leave must start and end in the same year. Please submit two requests");
        }
        int days = workingDays(start, end);
        if (days <= 0) {
            throw new FieldValidationException("startDate", "The selected dates contain no working days");
        }

        int year = start.getYear();
        List<LeaveBalance> locked = lockEmployee(user, year);

        List<LeaveRequest> clashes = requests.findOverlapping(user, ACTIVE, start, end);
        if (!clashes.isEmpty()) {
            LeaveRequest c = clashes.get(0);
            throw new ConflictException("You already have " + c.getStatus().name().toLowerCase(Locale.ROOT)
                    + " leave from " + c.getStartDate() + " to " + c.getEndDate());
        }

        if (type.isTracked()) {
            LeaveBalance balance = balanceOf(locked, type);
            int reserved = pendingDays(requests.findPendingInYear(user, LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31)), type);
            int available = balance.getAllocated() - balance.getUsed() - reserved;
            if (days > available) {
                throw new BusinessRuleException("Not enough " + type.getLabel().toLowerCase(Locale.ROOT)
                        + " left: you asked for " + days + " day(s) but have " + Math.max(available, 0));
            }
        }
        return LeaveResponse.from(requests.save(new LeaveRequest(user, type, start, end, days, dto.reason().trim())));
    }

    @PreAuthorize(ANY_STAFF)
    @Transactional(readOnly = true)
    public List<LeaveResponse> myLeave(String username, LeaveStatus status, String type) {
        return filter(requests.findByEmployeeOrderByStartDateDesc(findUser(username)), status, type, null).stream()
                .map(LeaveResponse::from).collect(Collectors.toList());
    }

    @PreAuthorize(ANY_STAFF)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaveResponse cancel(String username, Long id) {
        LeaveRequestRepository.Peek peek = peek(id);
        if (!peek.getEmployeeUsername().equals(username)) {
            throw new ResourceNotFoundException("Leave request not found with id " + id); // never reveal others' requests
        }
        List<LeaveBalance> locked = lockEmployee(findUserById(peek.getEmployeeId()), peek.getStartDate().getYear());
        LeaveRequest request = requests.findByIdForUpdate(id).orElseThrow();

        if (request.getStatus() == LeaveStatus.PENDING) {
            request.cancel();
        } else if (request.getStatus() == LeaveStatus.APPROVED) {
            if (!request.getStartDate().isAfter(LocalDate.now())) {
                throw new BusinessRuleException("Leave that has already started cannot be cancelled");
            }
            if (request.getLeaveType().isTracked()) {
                LeaveBalance balance = balanceOf(locked, request.getLeaveType());
                balance.setUsed(Math.max(0, balance.getUsed() - request.getDays()));
            }
            request.cancel();
        } else {
            throw new InvalidStatusException("A " + request.getStatus().name().toLowerCase(Locale.ROOT)
                    + " request cannot be cancelled");
        }
        return LeaveResponse.from(request);
    }

    // ---------- manager ----------

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public List<LeaveResponse> allLeave(LeaveStatus status, String type, String search) {
        return filter(requests.findAllByOrderBySubmittedAtDesc(), status, type, search).stream()
                .map(LeaveResponse::from).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaveResponse approve(Long id, String managerUsername, String note) {
        LeaveRequestRepository.Peek peek = peek(id);
        List<LeaveBalance> locked = lockEmployee(findUserById(peek.getEmployeeId()), peek.getStartDate().getYear());
        LeaveRequest request = lockPending(id, managerUsername);

        boolean clash = requests.findOverlapping(request.getEmployee(), EnumSet.of(LeaveStatus.APPROVED),
                request.getStartDate(), request.getEndDate()).stream().anyMatch(o -> !o.getId().equals(id));
        if (clash) {
            throw new ConflictException("Approving this would overlap leave that is already approved for "
                    + request.getEmployee().getFullname());
        }
        if (request.getLeaveType().isTracked()) {
            LeaveBalance balance = balanceOf(locked, request.getLeaveType());
            if (balance.getUsed() + request.getDays() > balance.getAllocated()) {
                throw new BusinessRuleException(request.getEmployee().getFullname() + " does not have enough "
                        + request.getLeaveType().getLabel().toLowerCase(Locale.ROOT) + " left");
            }
            balance.setUsed(balance.getUsed() + request.getDays());
        }
        request.decide(LeaveStatus.APPROVED, managerUsername, clean(note));
        return LeaveResponse.from(request);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaveResponse reject(Long id, String managerUsername, String note) {
        LeaveRequestRepository.Peek peek = peek(id);
        lockEmployee(findUserById(peek.getEmployeeId()), peek.getStartDate().getYear());
        LeaveRequest request = lockPending(id, managerUsername);
        request.decide(LeaveStatus.REJECTED, managerUsername, clean(note));
        return LeaveResponse.from(request);
    }

    /** Approved leave overlapping the month, for the team calendar. */
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public List<LeaveResponse> calendar(YearMonth month) {
        return requests.findApprovedBetween(month.atDay(1), month.atEndOfMonth()).stream()
                .map(LeaveResponse::from).collect(Collectors.toList());
    }

    // ---------- helpers ----------

    /** Loads the request under a row lock and checks this manager may decide it right now. */
    private LeaveRequest lockPending(Long id, String managerUsername) {
        LeaveRequest request = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id " + id));
        if (request.getEmployee().getUsername().equals(managerUsername)) {
            throw new ForbiddenActionException("You cannot approve or reject your own leave request");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new InvalidStatusException("This request is already " + request.getStatus().name().toLowerCase(Locale.ROOT)
                    + " and cannot be decided again");
        }
        return request;
    }

    /** Makes sure the employee has a balance row for every leave type this year, and returns them. */
    private List<LeaveBalance> ensureBalances(User user, int year) {
        List<LeaveBalance> existing = balances.findByUserAndLeaveYearOrderByLeaveType(user, year);
        List<LeaveBalance> missing = Arrays.stream(LeaveType.values())
                .filter(t -> existing.stream().noneMatch(b -> b.getLeaveType() == t))
                .map(t -> new LeaveBalance(user, year, t, t.getYearlyAllowance()))
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            return existing;
        }
        balances.saveAllAndFlush(missing);
        return balances.findByUserAndLeaveYearOrderByLeaveType(user, year);
    }

    /**
     * Locks the employee's balance rows. The locking query runs FIRST, before anything else has loaded those rows
     * into this transaction, so the values we then read are the latest committed ones. Missing rows (a new
     * employee, or a new year) are created and locked afterwards.
     */
    private List<LeaveBalance> lockEmployee(User user, int year) {
        List<LeaveBalance> locked = balances.lockForEmployee(user, year);
        if (locked.size() < LeaveType.values().length) {
            ensureBalances(user, year);
            locked = balances.lockForEmployee(user, year);
        }
        return locked;
    }

    private LeaveRequestRepository.Peek peek(Long id) {
        return requests.peek(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id " + id));
    }

    private User findUserById(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private LeaveBalance balanceOf(List<LeaveBalance> rows, LeaveType type) {
        Optional<LeaveBalance> row = rows.stream().filter(b -> b.getLeaveType() == type).findFirst();
        return row.orElseThrow(() -> new IllegalStateException("Missing balance row for " + type));
    }

    private int pendingDays(List<LeaveRequest> pending, LeaveType type) {
        return pending.stream().filter(r -> r.getLeaveType() == type).mapToInt(LeaveRequest::getDays).sum();
    }

    private User findUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private LeaveType parseType(String value) {
        try {
            return LeaveType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("leaveType", "Unknown leave type '" + value + "'");
        }
    }

    private String clean(String note) {
        return (note == null || note.isBlank()) ? null : note.trim();
    }

    private List<LeaveRequest> filter(List<LeaveRequest> all, LeaveStatus status, String type, String search) {
        LeaveType wanted = (type == null || type.isBlank()) ? null : parseType(type);
        String term = (search == null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> wanted == null || r.getLeaveType() == wanted)
                .filter(r -> term.isEmpty()
                        || r.getEmployee().getFullname().toLowerCase(Locale.ROOT).contains(term)
                        || r.getEmployee().getUsername().toLowerCase(Locale.ROOT).contains(term)
                        || r.getReason().toLowerCase(Locale.ROOT).contains(term))
                .collect(Collectors.toList());
    }
}
