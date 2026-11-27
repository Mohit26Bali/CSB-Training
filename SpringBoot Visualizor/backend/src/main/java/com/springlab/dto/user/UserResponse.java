package com.springlab.dto.user;

import com.springlab.entity.Role;

public record UserResponse(Long id, String username, String email, Role role) {
}
