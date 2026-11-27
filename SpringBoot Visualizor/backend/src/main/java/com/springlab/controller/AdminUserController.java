package com.springlab.controller;

import com.springlab.dto.admin.AdminUserResponse;
import com.springlab.dto.admin.UpdateEnabledRequest;
import com.springlab.dto.admin.UpdateRoleRequest;
import com.springlab.dto.common.PageResponse;
import com.springlab.entity.Role;
import com.springlab.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) Role role,
                                                @RequestParam(required = false) String q,
                                                @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return adminUserService.list(role, q, pageable);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return adminUserService.changeRole(id, request.role(), jwt.getSubject());
    }

    @PatchMapping("/{id}/enabled")
    public AdminUserResponse changeEnabled(@PathVariable Long id, @Valid @RequestBody UpdateEnabledRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return adminUserService.changeEnabled(id, request.enabled(), jwt.getSubject());
    }
}
