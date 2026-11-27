package com.icc.common.error;

/** The caller is logged in but may not do this (a rule inside the service, not a role check). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
