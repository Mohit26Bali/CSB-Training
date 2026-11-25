package com.csb.banking.dto;

/**
 * Answer of login and register: the JWT plus enough profile data for the UI to show who is logged in.
 *
 * <p>Built by {@code service/AuthService}; the token is produced by {@code security/JwtService} and must be sent
 * back by the client as {@code Authorization: Bearer <token>}, which {@code security/JwtAuthenticationFilter}
 * validates on every request.
 */
public record AuthResponse(
        String token,
        long expiresInSeconds,
        String username,
        String fullName,
        String role) {
}
