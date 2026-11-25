package com.csb.cinescope.exception;

import org.springframework.http.HttpStatus;

/**
 * The request clashes with existing data (409): registering with a username or email that is already taken.
 * Thrown by {@code service/AuthService}.
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
