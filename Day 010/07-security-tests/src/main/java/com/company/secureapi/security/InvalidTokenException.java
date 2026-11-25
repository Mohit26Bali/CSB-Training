package com.company.secureapi.security;

import org.springframework.security.core.AuthenticationException;

/** A Bearer token was sent but cannot be accepted (expired, bad signature, malformed). */
public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
