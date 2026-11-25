package com.company.jwt.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request and response bodies of the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
        @Override
        public String toString() {
            return "LoginRequest[" + username + ", ***]";
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
        @Override
        public String toString() {
            return "RefreshRequest[***]";
        }
    }

    /**
     * The answer to login and refresh (field names as in OAuth 2.0, RFC 6749):
     * the client sends accessToken as "Authorization: Bearer ..." and keeps
     * refreshToken to get a new pair when the access token expires.
     */
    public record TokenResponse(String tokenType, String accessToken, long expiresIn,
                                String refreshToken, long refreshExpiresIn) {
    }

    public record MeResponse(String username, List<String> authorities, String tokenId, Instant tokenExpiresAt) {
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    public record UserSummary(Long id, String username, String fullName, boolean enabled, List<String> roles) {
    }

    public record DirectoryEntry(String fullName, String email) {
    }
}
