package com.springlab.dto.admin;

import com.springlab.entity.Role;

import java.time.Instant;

public record AdminUserResponse(Long id, String username, String email, Role role, boolean enabled, Instant createdAt) {
}
