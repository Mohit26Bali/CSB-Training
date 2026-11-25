package com.csb.cinescope.dto;

import com.csb.cinescope.entity.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/admin/users/{id}}: what an administrator may change about an account - name, country, role, and
 * whether it is enabled. (Username, email and password are not changed here.) Applied by {@code service/AdminUserService#update},
 * which refuses changes that would leave the system without an enabled administrator or lock the acting administrator out.
 */
public record AdminUpdateUserRequest(
        @NotBlank(message = "display name is required")
        @Size(max = 60, message = "display name must be at most 60 characters")
        String displayName,

        @NotBlank(message = "region is required")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "region must be a two-letter country code such as IN or US")
        String region,

        @NotNull(message = "role is required")
        Role role,

        @NotNull(message = "enabled is required")
        Boolean enabled) {
}
