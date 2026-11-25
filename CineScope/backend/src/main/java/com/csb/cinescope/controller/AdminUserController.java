package com.csb.cinescope.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.AdminCreateUserRequest;
import com.csb.cinescope.dto.AdminUpdateUserRequest;
import com.csb.cinescope.dto.AdminUserView;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Account management for administrators - the full create / read / update / delete of accounts. No logic here;
 * {@code service/AdminUserService} applies the rules. Every URL under {@code /api/admin/} needs ROLE_ADMIN
 * ({@code config/SecurityConfig}: 401 without a login, 403 for an ordinary user), and the service checks it again.
 *
 * <pre>
 * GET    /api/admin/users          list / search: ?q= &amp;role= &amp;enabled= &amp;page= &amp;size=
 * GET    /api/admin/users/{id}     one account
 * POST   /api/admin/users          create an account with a role
 * PUT    /api/admin/users/{id}     change name, country, role, enabled
 * DELETE /api/admin/users/{id}     delete an account and its watchlist
 * </pre>
 * The frontend page is pages/AdminUsersPage.
 */
@Tag(name = "Admin users", description = "Manage accounts (administrators only)")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService admin;

    public AdminUserController(AdminUserService admin) {
        this.admin = admin;
    }

    @Operation(summary = "List accounts", description = "q matches username, email or name; role USER or ADMIN; enabled true or false; sorted by username.")
    @GetMapping
    public PageResult<AdminUserView> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Role role,
                                          @RequestParam(required = false) Boolean enabled,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return admin.list(q, role, enabled, page, size);
    }

    @Operation(summary = "One account")
    @GetMapping("/{id}")
    public AdminUserView get(@PathVariable long id) {
        return admin.get(id);
    }

    @Operation(summary = "Create an account", description = "Same rules as sign-up, plus a role.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView create(@Valid @RequestBody AdminCreateUserRequest request, Authentication authentication) {
        return admin.create(request, authentication.getName());
    }

    @Operation(summary = "Change an account", description = "Name, country, role and enabled. You cannot demote or disable yourself, and the last enabled administrator cannot be demoted or disabled.")
    @PutMapping("/{id}")
    public AdminUserView update(@PathVariable long id, @Valid @RequestBody AdminUpdateUserRequest request, Authentication authentication) {
        return admin.update(id, request, authentication.getName());
    }

    @Operation(summary = "Delete an account", description = "Removes the account and its watchlist. You cannot delete yourself here, and the last enabled administrator cannot be deleted.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication authentication) {
        admin.delete(id, authentication.getName());
    }
}
