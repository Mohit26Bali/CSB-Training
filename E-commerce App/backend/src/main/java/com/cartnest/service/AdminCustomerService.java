package com.cartnest.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.admin.AdminCustomerRow;
import com.cartnest.dto.admin.PageResponse;
import com.cartnest.entity.Role;
import com.cartnest.entity.User;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.CustomerStats;
import com.cartnest.repository.OrderRepository;
import com.cartnest.repository.UserRepository;

@Service
@Transactional
public class AdminCustomerService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public AdminCustomerService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /** Customers (newest first), searchable by name or email, with their order count and amount spent. */
    @Transactional(readOnly = true)
    public PageResponse<AdminCustomerRow> list(String q, int page, int size) {
        AdminProductService.checkPaging(page, size);
        Specification<User> spec = (root, query, cb) -> cb.equal(root.get("role"), Role.CUSTOMER);
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)));
        }
        Page<User> users = userRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        Map<Long, CustomerStats> stats = users.isEmpty() ? Map.of()
                : orderRepository.statsForUsers(users.map(User::getId).toList()).stream()
                        .collect(Collectors.toMap(CustomerStats::userId, Function.identity()));
        List<AdminCustomerRow> rows = users.getContent().stream().map(u -> {
            CustomerStats s = stats.get(u.getId());
            return new AdminCustomerRow(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isEnabled(),
                    u.getCreatedAt(), s == null ? 0 : s.orders(), s == null ? BigDecimal.ZERO : s.spent());
        }).toList();
        return new PageResponse<>(rows, users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages());
    }

    /** A disabled customer cannot log in, and their current token stops working on the next request. */
    public void setEnabled(Long id, boolean enabled) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer " + id + " not found"));
        if (user.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("Only customer accounts can be enabled or disabled here");
        }
        user.setEnabled(enabled);
    }
}
