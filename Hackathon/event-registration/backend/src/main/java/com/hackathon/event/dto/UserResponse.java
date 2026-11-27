package com.hackathon.event.dto;

public class UserResponse {

    private final String username;
    private final String fullname;
    private final String role;

    public UserResponse(String username, String fullname, String role) {
        this.username = username;
        this.fullname = fullname;
        this.role = role;
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
