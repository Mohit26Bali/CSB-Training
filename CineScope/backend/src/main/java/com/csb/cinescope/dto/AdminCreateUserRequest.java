package com.csb.cinescope.dto;

import com.csb.cinescope.entity.Role;
import com.csb.cinescope.security.StrongPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/admin/users}: an administrator creating an account for someone, with a role. The same rules as
 * self sign-up ({@code dto/RegisterRequest}) apply, including the password strength. {@code region} is optional (the default
 * country is used when it is missing). Applied by {@code service/AdminUserService#create}.
 */
public record AdminCreateUserRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be 3 to 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]*$", message = "username may contain letters, digits, '.', '_' and '-' only")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 100, message = "email must be at most 100 characters")
        String email,

        @NotBlank(message = "display name is required")
        @Size(max = 60, message = "display name must be at most 60 characters")
        String displayName,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 20, message = "password must be 8 to 20 characters")
        @StrongPassword
        String password,

        @Pattern(regexp = "^([A-Za-z]{2})?$", message = "region must be a two-letter country code such as IN or US")
        String region,

        @NotNull(message = "role is required")
        Role role) {
}
