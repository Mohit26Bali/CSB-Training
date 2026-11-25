package com.company.secureapi.security;

/** The principal of a token-authenticated request, from the claims sub and emp. */
public record AuthenticatedUser(String username, Long employeeId) {

    @Override
    public String toString() {
        return username;
    }
}
