package com.csb.cinescope.dto;

/**
 * Answer of login and register: the JWT and enough profile data for the UI to show who is logged in. The client sends the
 * token back as {@code Authorization: Bearer <token>}; {@code security/JwtAuthenticationFilter} validates it on every request.
 */
public record AuthResponse(String token, long expiresInSeconds, String username, String displayName, String region, String role) {
}
