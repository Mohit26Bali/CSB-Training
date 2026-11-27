package com.hackathon.warranty.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.warranty.dto.ClaimRequest;
import com.hackathon.warranty.dto.ClaimResponse;
import com.hackathon.warranty.dto.StatusCount;
import com.hackathon.warranty.entity.Claim;
import com.hackathon.warranty.entity.ClaimPriority;
import com.hackathon.warranty.entity.ClaimStatus;
import com.hackathon.warranty.entity.Product;
import com.hackathon.warranty.entity.User;
import com.hackathon.warranty.exception.BusinessRuleException;
import com.hackathon.warranty.exception.ConflictException;
import com.hackathon.warranty.exception.FieldValidationException;
import com.hackathon.warranty.exception.InvalidStatusException;
import com.hackathon.warranty.exception.ResourceNotFoundException;
import com.hackathon.warranty.repo.ClaimRepository;
import com.hackathon.warranty.repo.ProductRepository;
import com.hackathon.warranty.repo.UserRepository;

@Service
public class ClaimService {

    private static final EnumSet<ClaimStatus> ACTIVE = EnumSet.of(ClaimStatus.PENDING, ClaimStatus.APPROVED, ClaimStatus.IN_REPAIR);

    private final ClaimRepository claims;
    private final ProductRepository products;
    private final UserRepository users;

    public ClaimService(ClaimRepository claims, ProductRepository products, UserRepository users) {
        this.claims = claims;
        this.products = products;
        this.users = users;
    }

    // ---------- customer ----------

    /**
     * The product row is locked first, so two claims for the same product at the same moment are handled one after
     * the other and the second one sees the first as an open claim.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse file(String username, ClaimRequest request) {
        User customer = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        ClaimPriority priority = parsePriority(request.priority());

        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + request.productId()));
        if (!product.getOwner().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Product not found with id " + request.productId()); // never reveal others' products
        }
        LocalDate today = LocalDate.now();
        if (!product.isWarrantyActive(today)) {
            throw new BusinessRuleException("The warranty for " + product.getName() + " expired on " + product.getWarrantyEnd()
                    + ", so a claim cannot be filed");
        }
        if (claims.existsByProductAndStatusIn(product, ACTIVE)) {
            throw new ConflictException("There is already an open claim for " + product.getName()
                    + ". Wait until it is completed or rejected before filing another");
        }
        return ClaimResponse.from(claims.save(new Claim(product, customer, request.issueDescription().trim(), priority)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<ClaimResponse> myClaims(String username, ClaimStatus status) {
        User customer = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return claims.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .filter(c -> status == null || c.getStatus() == status)
                .map(ClaimResponse::from).collect(Collectors.toList());
    }

    // ---------- service agent ----------

    /** Newest first; the pending queue is ordered by priority (high first), then by who has waited longest. */
    @PreAuthorize("hasRole('SERVICE_AGENT')")
    @Transactional(readOnly = true)
    public List<ClaimResponse> list(ClaimStatus status, String priority, String search) {
        ClaimPriority wanted = (priority == null || priority.isBlank()) ? null : parsePriority(priority);
        String term = (search == null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        Comparator<Claim> order = status == ClaimStatus.PENDING
                ? Comparator.comparingInt((Claim c) -> c.getPriority().getRank()).reversed().thenComparing(Claim::getCreatedAt)
                : Comparator.comparing(Claim::getCreatedAt).reversed();
        return claims.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> wanted == null || c.getPriority() == wanted)
                .filter(c -> term.isEmpty()
                        || c.getProduct().getName().toLowerCase(Locale.ROOT).contains(term)
                        || c.getProduct().getBrand().toLowerCase(Locale.ROOT).contains(term)
                        || c.getProduct().getSerialNumber().toLowerCase(Locale.ROOT).contains(term)
                        || c.getCustomer().getFullname().toLowerCase(Locale.ROOT).contains(term)
                        || c.getCustomer().getUsername().toLowerCase(Locale.ROOT).contains(term)
                        || c.getIssueDescription().toLowerCase(Locale.ROOT).contains(term))
                .sorted(order)
                .map(ClaimResponse::from).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SERVICE_AGENT')")
    @Transactional(readOnly = true)
    public List<StatusCount> summary() {
        Map<ClaimStatus, Long> counts = claims.findAll().stream()
                .collect(Collectors.groupingBy(Claim::getStatus, Collectors.counting()));
        return Arrays.stream(ClaimStatus.values())
                .map(s -> new StatusCount(s.name(), counts.getOrDefault(s, 0L))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SERVICE_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse approve(Long id, String agent) {
        Claim claim = lock(id, ClaimStatus.PENDING, "approved");
        claim.approve(agent);
        return ClaimResponse.from(claim);
    }

    @PreAuthorize("hasRole('SERVICE_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse reject(Long id, String agent, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new FieldValidationException("reason", "A reason is required when rejecting a claim");
        }
        Claim claim = lock(id, ClaimStatus.PENDING, "rejected");
        claim.reject(agent, reason.trim());
        return ClaimResponse.from(claim);
    }

    @PreAuthorize("hasRole('SERVICE_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse startRepair(Long id) {
        Claim claim = lock(id, ClaimStatus.APPROVED, "moved to repair");
        claim.startRepair();
        return ClaimResponse.from(claim);
    }

    @PreAuthorize("hasRole('SERVICE_AGENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse complete(Long id, String note) {
        Claim claim = lock(id, ClaimStatus.IN_REPAIR, "completed");
        claim.complete((note == null || note.isBlank()) ? null : note.trim());
        return ClaimResponse.from(claim);
    }

    // ---------- helpers ----------

    /** Loads the claim under a row lock and checks it is in the status the action needs. */
    private Claim lock(Long id, ClaimStatus required, String action) {
        Claim claim = claims.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found with id " + id));
        if (claim.getStatus() != required) {
            throw new InvalidStatusException("Only " + required.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " claims can be " + action + "; this one is " + claim.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return claim;
    }

    private ClaimPriority parsePriority(String value) {
        if (value == null || value.isBlank()) {
            return ClaimPriority.MEDIUM;
        }
        try {
            return ClaimPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("priority", "Priority must be LOW, MEDIUM or HIGH");
        }
    }
}
