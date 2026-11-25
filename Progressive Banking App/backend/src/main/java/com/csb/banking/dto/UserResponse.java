package com.csb.banking.dto;

/**
 * The logged-in user, returned by {@code GET /api/auth/me} ({@code controller/AuthController}, built by
 * {@code service/AuthService#currentUser}). Lets the UI restore its session from a stored token.
 */
public record UserResponse(String username, String fullName, String role) {
}
