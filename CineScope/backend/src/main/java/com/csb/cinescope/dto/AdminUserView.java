package com.csb.cinescope.dto;

import java.time.LocalDateTime;

/**
 * One account as an administrator sees it ({@code /api/admin/users}): everything about the person except the password hash,
 * plus how many titles are on their watchlist. Built by {@code service/AdminUserService}.
 */
public record AdminUserView(
        long id,
        String username,
        String email,
        String displayName,
        String region,
        String role,
        boolean enabled,
        LocalDateTime createdAt,
        long watchlistCount) {
}
