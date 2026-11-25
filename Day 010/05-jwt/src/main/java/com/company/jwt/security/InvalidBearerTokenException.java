package com.company.jwt.security;

import org.springframework.security.core.AuthenticationException;

/** A Bearer token was sent but is not acceptable; the message says why (expired, bad signature ...). */
public class InvalidBearerTokenException extends AuthenticationException {

    public InvalidBearerTokenException(String message) {
        super(message);
    }
}
