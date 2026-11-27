package com.icc.assignment.exception;

import com.icc.common.error.ApiException;
import com.icc.common.error.ErrorCode;

/**
 * One message for every login failure (unknown user, wrong password, inactive user),
 * so an attacker cannot learn which usernames exist.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(ErrorCode.UNAUTHORIZED, "Invalid username or password");
    }
}
