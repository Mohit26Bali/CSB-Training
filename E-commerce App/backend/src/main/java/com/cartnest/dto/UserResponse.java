package com.cartnest.dto;

import com.cartnest.entity.Role;

public record UserResponse(Long id, String fullName, String email, String phone, Role role) {
}
