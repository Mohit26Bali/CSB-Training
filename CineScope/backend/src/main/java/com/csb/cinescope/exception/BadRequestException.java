package com.csb.cinescope.exception;

import org.springframework.http.HttpStatus;

/**
 * A request parameter makes no sense, for example an unknown media type or category (400).
 * Thrown by {@code service/CatalogService}.
 */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
