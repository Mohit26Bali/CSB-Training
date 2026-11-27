package com.hackathon.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.procurement.dto.CategoryTotal;
import com.hackathon.procurement.dto.ProcurementDashboard;
import com.hackathon.procurement.dto.PurchaseResponse;
import com.hackathon.procurement.dto.RequestSubmission;
import com.hackathon.procurement.dto.StatusTotal;
import com.hackathon.procurement.entity.BudgetGate;
import com.hackathon.procurement.entity.ItemCategory;
import com.hackathon.procurement.entity.PurchaseRequest;
import com.hackathon.procurement.entity.RequestStatus;
import com.hackathon.procurement.entity.User;
import com.hackathon.procurement.exception.ConflictException;
import com.hackathon.procurement.exception.FieldValidationException;
import com.hackathon.procurement.exception.ForbiddenActionException;
import com.hackathon.procurement.exception.InvalidStatusException;
import com.hackathon.procurement.exception.ResourceNotFoundException;
import com.hackathon.procurement.repo.BudgetGateRepository;
import com.hackathon.procurement.repo.PurchaseRequestRepository;
import com.hackathon.procurement.repo.UserRepository;

@Service
public class ProcurementService {

    private final PurchaseRequestRepository requests;
    private final UserRepository users;
    private final BudgetGateRepository gate;
    private final BigDecimal monthlyBudget;

    public ProcurementService(PurchaseRequestRepository requests, UserRepository users, BudgetGateRepository gate,
                              @Value("${app.budget.monthly-limit}") BigDecimal monthlyBudget) {
        this.requests = requests;
        this.users = users;
        this.gate = gate;
        this.monthlyBudget = monthlyBudget;
    }

    // ---------- employee ----------

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public PurchaseResponse submit(String username, RequestSubmission dto) {
        User employee = findUser(username);
        ItemCategory category = parseCategory(dto.category());
        PurchaseRequest request = new PurchaseRequest(employee, dto.itemName().trim(), category, dto.quantity(),
                dto.estimatedUnitCost(), dto.justification().trim());
        return PurchaseResponse.from(requests.save(request));
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<PurchaseResponse> myRequests(String username, RequestStatus status, String category, String search) {
        return filter(requests.findByEmployeeOrderBySubmittedAtDesc(findUser(username)), status, category, search)
                .stream().map(PurchaseResponse::from).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<StatusTotal> mySummary(String username) {
        return totalsByStatus(requests.findByEmployeeOrderBySubmittedAtDesc(findUser(username)));
    }

    public List<Map<String, String>> categories() {
        return Arrays.stream(ItemCategory.values())
                .map(c -> Map.of("value", c.name(), "label", c.getLabel()))
                .collect(Collectors.toList());
    }

    // ---------- procurement manager ----------

    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    @Transactional(readOnly = true)
    public List<PurchaseResponse> allRequests(RequestStatus status, String category, String search) {
        return filter(requests.findAllByOrderBySubmittedAtDesc(), status, category, search).stream()
                .map(PurchaseResponse::from).collect(Collectors.toList());
    }

    /**
     * Approving checks the monthly budget, so it first locks the budget gate (always before the request row):
     * two approvals cannot both see the same remaining budget.
     */
    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseResponse approve(Long id, String manager) {
        gate.lock().orElseGet(() -> gate.saveAndFlush(BudgetGate.create()));
        PurchaseRequest request = lockPending(id, manager);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal committed = committedInMonth(YearMonth.from(now));
        BigDecimal total = request.getEstimatedTotal();
        if (committed.add(total).compareTo(monthlyBudget) > 0) {
            BigDecimal remaining = monthlyBudget.subtract(committed).max(BigDecimal.ZERO);
            throw new ConflictException("Approving this request (" + money(total) + ") would exceed this month's budget of "
                    + money(monthlyBudget) + ". Remaining: " + money(remaining));
        }
        request.approve(manager);
        return PurchaseResponse.from(request);
    }

    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseResponse reject(Long id, String reason, String manager) {
        if (reason == null || reason.isBlank()) {
            throw new FieldValidationException("reason", "A reason is required when rejecting a request");
        }
        PurchaseRequest request = lockPending(id, manager);
        request.reject(manager, reason.trim());
        return PurchaseResponse.from(request);
    }

    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseResponse markOrdered(Long id, String orderReference) {
        PurchaseRequest request = lock(id);
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new InvalidStatusException("Only approved requests can be ordered; this one is "
                    + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        request.markOrdered((orderReference == null || orderReference.isBlank()) ? null : orderReference.trim());
        return PurchaseResponse.from(request);
    }

    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseResponse markDelivered(Long id) {
        PurchaseRequest request = lock(id);
        if (request.getStatus() != RequestStatus.ORDERED) {
            throw new InvalidStatusException("Only ordered requests can be marked delivered; this one is "
                    + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        request.markDelivered();
        return PurchaseResponse.from(request);
    }

    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    @Transactional(readOnly = true)
    public ProcurementDashboard dashboard() {
        List<PurchaseRequest> all = requests.findAllByOrderBySubmittedAtDesc();
        List<PurchaseRequest> committed = requests.findCommittedBetween(
                YearMonth.now().atDay(1).atStartOfDay(), YearMonth.now().plusMonths(1).atDay(1).atStartOfDay());
        BigDecimal spent = total(committed);

        List<CategoryTotal> byCategory = committed.stream()
                .collect(Collectors.groupingBy(PurchaseRequest::getCategory)).entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey().name(), e.getKey().getLabel(), e.getValue().size(), total(e.getValue())))
                .sorted(Comparator.comparing(CategoryTotal::amount).reversed())
                .collect(Collectors.toList());

        LocalDateTime oldestPending = all.stream().filter(r -> r.getStatus() == RequestStatus.PENDING)
                .map(PurchaseRequest::getSubmittedAt).min(Comparator.naturalOrder()).orElse(null);

        return new ProcurementDashboard(totalsByStatus(all), monthlyBudget, spent,
                monthlyBudget.subtract(spent).max(BigDecimal.ZERO), byCategory, oldestPending);
    }

    // ---------- helpers ----------

    private PurchaseRequest lock(Long id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase request not found with id " + id));
    }

    private PurchaseRequest lockPending(Long id, String manager) {
        PurchaseRequest request = lock(id);
        if (request.getEmployee().getUsername().equals(manager)) {
            throw new ForbiddenActionException("You cannot approve or reject your own request");
        }
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStatusException("This request is already " + request.getStatus().name().toLowerCase(Locale.ROOT)
                    + " and cannot be decided again");
        }
        return request;
    }

    private BigDecimal committedInMonth(YearMonth month) {
        return total(requests.findCommittedBetween(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay()));
    }

    private BigDecimal total(List<PurchaseRequest> list) {
        return list.stream().map(PurchaseRequest::getEstimatedTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<StatusTotal> totalsByStatus(List<PurchaseRequest> list) {
        Map<RequestStatus, List<PurchaseRequest>> by = list.stream().collect(Collectors.groupingBy(PurchaseRequest::getStatus));
        return Arrays.stream(RequestStatus.values())
                .map(s -> new StatusTotal(s.name(), by.getOrDefault(s, List.of()).size(), total(by.getOrDefault(s, List.of()))))
                .collect(Collectors.toList());
    }

    private String money(BigDecimal amount) {
        return "₹" + String.format(Locale.US, "%,.2f", amount);
    }

    private User findUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private ItemCategory parseCategory(String value) {
        try {
            return ItemCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("category", "Unknown category '" + value + "'");
        }
    }

    private List<PurchaseRequest> filter(List<PurchaseRequest> all, RequestStatus status, String category, String search) {
        ItemCategory wanted = (category == null || category.isBlank()) ? null : parseCategory(category);
        String term = (search == null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> wanted == null || r.getCategory() == wanted)
                .filter(r -> term.isEmpty()
                        || r.getItemName().toLowerCase(Locale.ROOT).contains(term)
                        || r.getJustification().toLowerCase(Locale.ROOT).contains(term)
                        || r.getEmployee().getFullname().toLowerCase(Locale.ROOT).contains(term)
                        || r.getEmployee().getUsername().toLowerCase(Locale.ROOT).contains(term))
                .collect(Collectors.toList());
    }
}
