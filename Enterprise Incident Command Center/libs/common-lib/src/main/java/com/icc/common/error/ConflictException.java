package com.icc.common.error;

/** A valid request that clashes with the current state, for example a state change that is not allowed. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
