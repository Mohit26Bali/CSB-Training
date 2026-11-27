package com.springlab.dto.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresIn) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
