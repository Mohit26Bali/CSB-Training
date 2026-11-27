package com.icc.common.error;

public class NotFoundException extends ApiException {

    public NotFoundException(String what, Object id) {
        super(ErrorCode.NOT_FOUND, what + " " + id + " not found");
    }

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
