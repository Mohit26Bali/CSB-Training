package com.company.jwt.security;

import io.jsonwebtoken.JwtException;

/** The token names a key id ("kid") that this server does not have (or names none). */
public class UnknownSigningKeyException extends JwtException {

    public UnknownSigningKeyException(String keyId) {
        super("Unknown signing key: " + keyId);
    }
}
