package com.cartnest.dto;

import java.time.Instant;

/** Send the token back as "Authorization: Bearer token" until expiresAt. */
public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
