package com.icc.common.error;

/** The input is wrong in a way that bean validation cannot express, for example an unknown category. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
