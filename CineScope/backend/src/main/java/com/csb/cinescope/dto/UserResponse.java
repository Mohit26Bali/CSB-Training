package com.csb.cinescope.dto;

/** The logged-in user ({@code GET /api/auth/me}), which lets the UI restore its session from a stored token. */
public record UserResponse(String username, String email, String displayName, String region, String role) {
}
