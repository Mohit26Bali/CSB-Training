package com.hackathon.maintenance.dto;

public class AuthResponse {

    private final String token;
    private final String tokenType = "Bearer";
    private final long expiresIn;
    private final String username;
    private final String fullname;
    private final String role;

    public AuthResponse(String token, long expiresIn, String username, String fullname, String role) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.username = username;
        this.fullname = fullname;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getUsername() {
        return username;
    }

    public String getFullname() {
        return fullname;
    }

    public String getRole() {
        return role;
    }
}
