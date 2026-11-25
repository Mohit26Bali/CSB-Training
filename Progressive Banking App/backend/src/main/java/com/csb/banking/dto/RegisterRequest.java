package com.csb.banking.dto;

import com.csb.banking.security.StrongPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register} (customer self-registration).
 *
 * <p>Received by {@code controller/AuthController}; {@code service/AuthService#register} splits it into a
 * {@code entity/User} (username, password hash, full name) and a {@code entity/Customer} (email, mobile, address).
 * Validation rules follow section 13 of the project description: name not empty, valid email and mobile number.
 * The password is capped at 20 characters (a product choice; BCrypt itself could use 72); its strength rules are
 * {@code security/PasswordPolicy} (applied through {@code @StrongPassword}).
 */
public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be 3 to 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]*$", message = "username may contain letters, digits, '.', '_' and '-' only")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 20, message = "password must be 8 to 20 characters")
        @StrongPassword
        String password,

        @NotBlank(message = "full name is required")
        @Size(max = 100, message = "full name must be at most 100 characters")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 100, message = "email must be at most 100 characters")
        String email,

        @NotBlank(message = "mobile number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "mobile number must be 10 digits starting with 6-9")
        String mobile,

        @NotBlank(message = "address is required")
        @Size(max = 255, message = "address must be at most 255 characters")
        String address) {
}
