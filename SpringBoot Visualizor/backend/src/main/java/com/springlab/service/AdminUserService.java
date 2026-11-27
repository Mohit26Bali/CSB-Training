package com.springlab.service;

import com.springlab.dto.admin.AdminUserResponse;
import com.springlab.dto.common.PageResponse;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.exception.InvalidRequestException;
import com.springlab.exception.ResourceNotFoundException;
import com.springlab.mapper.UserMapper;
import com.springlab.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Administrative user management. Authorization (ADMIN only) is enforced at the web layer; the invariants below
 * (no self-demotion/self-disable, never zero active administrators) are enforced here so they hold for every caller.
 */
@Service
public class AdminUserService {

    /** Whitelist: sorting by e.g. passwordHash must not be possible (ordering would leak information about it). */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "username", "email", "role", "enabled", "createdAt");

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(Role role, String query, Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed: " + String.join(", ", new java.util.TreeSet<>(SORTABLE_PROPERTIES)));
            }
        });
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
        Page<AdminUserResponse> page = userRepository.search(role, normalizedQuery, pageable)
                .map(UserMapper::toAdminResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long id) {
        return userRepository.findById(id).map(UserMapper::toAdminResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public AdminUserResponse changeRole(Long targetId, Role newRole, String actingUsername) {
        MutationContext ctx = begin(targetId, actingUsername);
        User target = ctx.target();

        if (ctx.isSelf() && newRole != Role.ADMIN) {
            throw new BusinessRuleViolationException("Administrators cannot demote themselves");
        }
        if (target.getRole() == Role.ADMIN && target.isEnabled() && newRole != Role.ADMIN
                && ctx.activeAdminCount() <= 1) {
            throw new BusinessRuleViolationException("Cannot demote the last active administrator");
        }
        target.setRole(newRole); // managed entity: flushed on commit by dirty checking
        return UserMapper.toAdminResponse(target);
    }

    @Transactional
    public AdminUserResponse changeEnabled(Long targetId, boolean enabled, String actingUsername) {
        MutationContext ctx = begin(targetId, actingUsername);
        User target = ctx.target();

        if (ctx.isSelf() && !enabled) {
            throw new BusinessRuleViolationException("Administrators cannot disable their own account");
        }
        if (target.getRole() == Role.ADMIN && target.isEnabled() && !enabled && ctx.activeAdminCount() <= 1) {
            throw new BusinessRuleViolationException("Cannot disable the last active administrator");
        }
        target.setEnabled(enabled);
        return UserMapper.toAdminResponse(target);
    }

    /**
     * Step 1 takes row locks on all active administrators, step 2 loads the target. Doing it in this order means an
     * administrator target is read AFTER any concurrent transaction that changed it has committed, so the counts used
     * by the last-administrator rule are never stale.
     */
    private MutationContext begin(Long targetId, String actingUsername) {
        List<User> activeAdmins = userRepository.findActiveAdminsForUpdate();
        User actor = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new BadCredentialsException("Acting user no longer exists"));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new MutationContext(actor, target, activeAdmins.size());
    }

    private record MutationContext(User actor, User target, int activeAdminCount) {
        boolean isSelf() {
            return actor.getId().equals(target.getId());
        }
    }
}
