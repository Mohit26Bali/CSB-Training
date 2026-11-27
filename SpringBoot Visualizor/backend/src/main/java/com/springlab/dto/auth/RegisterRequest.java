package com.springlab.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

/**
 * Deliberately has no role field: public registration always creates a STUDENT.
 * Any "role" property sent by a client is ignored during deserialization.
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9_.-]*", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank
        @Email
        @Size(max = 120)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password) {

    /** BCrypt only uses the first 72 bytes and Spring Security rejects longer input; multi-byte chars count more. */
    @JsonIgnore
    @AssertTrue(message = "must be at most 72 bytes when UTF-8 encoded")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", password=***]";
    }
}
