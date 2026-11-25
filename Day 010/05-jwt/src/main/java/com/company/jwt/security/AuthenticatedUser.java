package com.company.jwt.security;

import java.time.Instant;

/**
 * The principal of a request authenticated by an access token, built from the
 * token's claims only (no database): who (sub), which token (jti) and until when (exp).
 */
public record AuthenticatedUser(String username, String tokenId, Instant tokenExpiresAt) {

    @Override
    public String toString() {
        return username;
    }
}
