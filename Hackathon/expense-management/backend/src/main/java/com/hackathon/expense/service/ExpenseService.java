package com.hackathon.expense.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.expense.dto.CategoryTotal;
import com.hackathon.expense.dto.ExpenseRequest;
import com.hackathon.expense.dto.ExpenseResponse;
import com.hackathon.expense.dto.ManagerDashboard;
import com.hackathon.expense.dto.StatusSummary;
import com.hackathon.expense.entity.Expense;
import com.hackathon.expense.entity.ExpenseCategory;
import com.hackathon.expense.entity.ExpenseStatus;
import com.hackathon.expense.entity.User;
import com.hackathon.expense.exception.FieldValidationException;
import com.hackathon.expense.exception.ForbiddenActionException;
import com.hackathon.expense.exception.InvalidStatusException;
import com.hackathon.expense.exception.ResourceNotFoundException;
import com.hackathon.expense.repo.ExpenseRepository;
import com.hackathon.expense.repo.UserRepository;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public ExpenseService(ExpenseRepository expenseRepository, UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    // ---------- employee ----------

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public ExpenseResponse submit(String username, ExpenseRequest request) {
        User employee = findUser(username);
        ExpenseCategory category = parseCategory(request.category());
        if (request.expenseDate().isAfter(LocalDate.now())) {
            throw new FieldValidationException("expenseDate", "Expense date cannot be in the future");
        }
        Expense expense = new Expense(employee, request.expenseDate(), category, request.amount(),
                request.description().trim());
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<ExpenseResponse> myExpenses(String username, ExpenseStatus status, String category, String search) {
        return filter(expenseRepository.findByEmployeeOrderBySubmittedAtDesc(findUser(username)),
                status, category, search).stream().map(ExpenseResponse::from).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public StatusSummary mySummary(String username) {
        return summarize(expenseRepository.findByEmployeeOrderBySubmittedAtDesc(findUser(username)));
    }

    // ---------- manager ----------

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public List<ExpenseResponse> allExpenses(ExpenseStatus status, String category, String search) {
        return filter(expenseRepository.findAllByOrderBySubmittedAtDesc(), status, category, search).stream()
                .map(ExpenseResponse::from).collect(Collectors.toList());
    }

    // READ_COMMITTED + row lock: the status check below always sees the latest committed decision.
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpenseResponse approve(Long id, String managerUsername) {
        Expense expense = lockPending(id, managerUsername);
        expense.decide(ExpenseStatus.APPROVED, managerUsername, null);
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpenseResponse reject(Long id, String reason, String managerUsername) {
        if (reason == null || reason.isBlank()) {
            throw new FieldValidationException("reason", "A reason is required when rejecting an expense");
        }
        Expense expense = lockPending(id, managerUsername);
        expense.decide(ExpenseStatus.REJECTED, managerUsername, reason.trim());
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public ManagerDashboard dashboard() {
        List<Expense> all = expenseRepository.findAllByOrderBySubmittedAtDesc();

        List<CategoryTotal> byCategory = all.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.APPROVED)
                .collect(Collectors.groupingBy(Expense::getCategory))
                .entrySet().stream()
                .map(entry -> new CategoryTotal(entry.getKey().name(), entry.getKey().getLabel(),
                        entry.getValue().size(), total(entry.getValue())))
                .sorted(Comparator.comparing(CategoryTotal::amount).reversed())
                .collect(Collectors.toList());

        LocalDateTime oldestPending = all.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.PENDING)
                .map(Expense::getSubmittedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new ManagerDashboard(summarize(all), byCategory, oldestPending);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> categories() {
        return Arrays.stream(ExpenseCategory.values())
                .map(c -> Map.of("value", c.name(), "label", c.getLabel()))
                .collect(Collectors.toList());
    }

    // ---------- helpers ----------

    /** Loads the expense with a row lock and checks that this manager may decide it right now. */
    private Expense lockPending(Long id, String managerUsername) {
        Expense expense = expenseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id " + id));
        if (expense.getEmployee().getUsername().equals(managerUsername)) {
            throw new ForbiddenActionException("You cannot approve or reject your own expense");
        }
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new InvalidStatusException("Expense is already " + expense.getStatus()
                    + " and cannot be processed again");
        }
        return expense;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private ExpenseCategory parseCategory(String value) {
        try {
            return ExpenseCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("category", "Unknown category '" + value + "'");
        }
    }

    /** Status, category and free-text filters; blank means "any". Text matches description and employee. */
    private List<Expense> filter(List<Expense> expenses, ExpenseStatus status, String category, String search) {
        ExpenseCategory wanted = (category == null || category.isBlank()) ? null : parseCategory(category);
        String term = (search == null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        return expenses.stream()
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> wanted == null || e.getCategory() == wanted)
                .filter(e -> term.isEmpty()
                        || e.getDescription().toLowerCase(Locale.ROOT).contains(term)
                        || e.getEmployee().getFullname().toLowerCase(Locale.ROOT).contains(term)
                        || e.getEmployee().getUsername().toLowerCase(Locale.ROOT).contains(term))
                .collect(Collectors.toList());
    }

    private StatusSummary summarize(List<Expense> expenses) {
        Map<ExpenseStatus, List<Expense>> byStatus = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getStatus));
        List<Expense> pending = byStatus.getOrDefault(ExpenseStatus.PENDING, List.of());
        List<Expense> approved = byStatus.getOrDefault(ExpenseStatus.APPROVED, List.of());
        List<Expense> rejected = byStatus.getOrDefault(ExpenseStatus.REJECTED, List.of());
        return new StatusSummary(pending.size(), total(pending), approved.size(), total(approved),
                rejected.size(), total(rejected));
    }

    private BigDecimal total(List<Expense> expenses) {
        return expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
