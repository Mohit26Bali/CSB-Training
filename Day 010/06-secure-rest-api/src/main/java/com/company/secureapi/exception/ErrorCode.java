package com.company.secureapi.exception;

/** Every error code the API can return. */
public enum ErrorCode {
    // security errors (written by JsonSecurityErrorHandlers)
    AUTH_REQUIRED,          // 401: no token
    AUTH_INVALID_TOKEN,     // 401: token missing parts, expired, bad signature ...
    AUTH_BAD_CREDENTIALS,   // 401: wrong username or password at login
    ACCESS_DENIED,          // 403: valid token, role not allowed

    // controller / business errors (written by GlobalExceptionHandler)
    VALIDATION_FAILED,      // 400
    EMPLOYEE_NOT_FOUND,     // 404
    USER_NOT_FOUND,         // 404
    DUPLICATE,              // 409
    MALFORMED_REQUEST       // 400: body missing or not JSON
}
