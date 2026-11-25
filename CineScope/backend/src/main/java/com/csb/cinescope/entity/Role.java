package com.csb.cinescope.entity;

/**
 * What an account may do. USER is every ordinary account: it can use the catalogue and manage its own profile and watchlist.
 * ADMIN can in addition manage all accounts through {@code /api/admin/users} ({@code controller/AdminUserController}).
 *
 * <p>Stored as text in {@code users.role}; turned into the Spring Security authority {@code ROLE_USER} / {@code ROLE_ADMIN}
 * by {@code service/CustomUserDetailsService}, which is what {@code config/SecurityConfig} (the {@code /api/admin/**} rule) and
 * {@code @PreAuthorize("hasRole('ADMIN')")} on {@code service/AdminUserService} check.
 */
public enum Role {
    USER,
    ADMIN;

    /** The Spring Security authority name for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
