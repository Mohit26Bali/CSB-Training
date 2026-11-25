package com.csb.cinescope.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.AdminCreateUserRequest;
import com.csb.cinescope.dto.AdminUpdateUserRequest;
import com.csb.cinescope.dto.AdminUserView;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.UserSpecifications;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.security.PasswordPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Account management for administrators: list and search accounts, look at one, create, change (name, country, role, enabled)
 * and delete. Called by {@code controller/AdminUserController}.
 *
 * <p>Authorization is enforced twice, on purpose: {@code config/SecurityConfig} requires ROLE_ADMIN for every
 * {@code /api/admin/**} URL, and this class repeats it with {@code @PreAuthorize} on every method, so the rule still holds if
 * the service is ever called from somewhere else. (Unit tests that build the class directly do not go through that check; the
 * API tests do.)
 *
 * <p>Safety rules, so administrators cannot lock everyone out: you cannot disable, demote or delete YOURSELF here (use the
 * profile page to leave), and the last enabled administrator can never be disabled, demoted or deleted. Changing a role or
 * disabling an account bumps its token version, which ends all of that person's sessions at once
 * ({@code entity/User}). Every change is written to the log with who did it.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository users;
    private final WatchlistItemRepository watchlist;
    private final PasswordEncoder passwordEncoder;
    private final CineScopeProperties properties;
    private final CommentService commentService;

    public AdminUserService(UserRepository users, WatchlistItemRepository watchlist, PasswordEncoder passwordEncoder,
                            CineScopeProperties properties, CommentService commentService) {
        this.users = users;
        this.watchlist = watchlist;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.commentService = commentService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminUserView> list(String text, Role role, Boolean enabled, int page, int size) {
        if (page < 1) {
            throw new BadRequestException("page must be 1 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<User> found = users.findAll(UserSpecifications.matching(text, role, enabled),
                PageRequest.of(page - 1, size, Sort.by("username").and(Sort.by("id"))));
        Map<Long, Long> counts = new HashMap<>();
        if (!found.isEmpty()) {
            watchlist.countByUserIds(found.getContent().stream().map(User::getId).toList())
                    .forEach(c -> counts.put(c.getUserId(), c.getTotal()));
        }
        List<AdminUserView> items = found.getContent().stream().map(u -> view(u, counts.getOrDefault(u.getId(), 0L))).toList();
        return new PageResult<>(items, page, Math.max(1, found.getTotalPages()), found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminUserView get(long id) {
        User user = find(id);
        return view(user, watchlist.countByUser(user));
    }

    @Transactional
    public AdminUserView create(AdminCreateUserRequest request, String actingAdmin) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String identityProblem = PasswordPolicy.identityProblem(request.password(), username, email);
        if (identityProblem != null) {
            throw new BadRequestException(identityProblem);
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        String region = request.region() == null || request.region().isBlank() ? properties.defaultRegion() : request.region().trim().toUpperCase(Locale.ROOT);
        User saved = users.save(new User(username, email, passwordEncoder.encode(request.password()), request.displayName().trim(), region, request.role()));
        log.info("Admin {} created account {} with role {}", actingAdmin, username, request.role());
        return view(saved, 0);
    }

    @Transactional
    public AdminUserView update(long id, AdminUpdateUserRequest request, String actingAdmin) {
        User user = find(id);
        boolean self = user.getUsername().equals(actingAdmin);
        boolean demotes = user.getRole() == Role.ADMIN && request.role() != Role.ADMIN;
        boolean disables = user.isEnabled() && !request.enabled();
        if (self && (demotes || disables)) {
            throw new BadRequestException("You cannot remove your own administrator rights or disable your own account");
        }
        if (user.getRole() == Role.ADMIN && user.isEnabled() && (demotes || disables)) {
            requireAnotherEnabledAdmin();
        }
        user.updateProfile(request.displayName().trim(), request.region().trim().toUpperCase(Locale.ROOT));
        user.changeRole(request.role());
        user.setEnabled(request.enabled());
        User saved = users.save(user);
        log.info("Admin {} updated account {} (role {}, enabled {})", actingAdmin, saved.getUsername(), saved.getRole(), saved.isEnabled());
        return view(saved, watchlist.countByUser(saved));
    }

    @Transactional
    public void delete(long id, String actingAdmin) {
        User user = find(id);
        if (user.getUsername().equals(actingAdmin)) {
            throw new BadRequestException("You cannot delete your own account here. Use your profile page");
        }
        if (user.getRole() == Role.ADMIN && user.isEnabled()) {
            requireAnotherEnabledAdmin();
        }
        commentService.deleteAllOf(user);
        watchlist.deleteByUser(user);
        users.delete(user);
        log.info("Admin {} deleted account {}", actingAdmin, user.getUsername());
    }

    /** Called before an enabled administrator stops being one: some other enabled administrator must remain. */
    private void requireAnotherEnabledAdmin() {
        if (users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("At least one enabled administrator is required");
        }
    }

    private User find(long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static AdminUserView view(User u, long watchlistCount) {
        return new AdminUserView(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getRegion(), u.getRole().name(),
                u.isEnabled(), u.getCreatedAt(), watchlistCount);
    }
}
