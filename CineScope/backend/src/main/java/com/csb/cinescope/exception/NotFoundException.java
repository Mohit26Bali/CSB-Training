package com.csb.cinescope.exception;

import org.springframework.http.HttpStatus;

/** The requested movie, show or person does not exist at TMDB (404). Thrown by {@code tmdb/TmdbHttpGateway}. */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
