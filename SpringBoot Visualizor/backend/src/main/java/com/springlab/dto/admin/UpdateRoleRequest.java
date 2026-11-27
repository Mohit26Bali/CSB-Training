package com.springlab.dto.admin;

import com.springlab.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull Role role) {
}
