package com.springlab.coding.execution;

import org.springframework.http.HttpStatus;

/** The user already has a submission queued or running (409). */
public class ExecutionBusyException extends ExecutionRejectedException {

    public ExecutionBusyException() {
        super(HttpStatus.CONFLICT, "You already have a submission that is waiting or running. Wait for it to finish or cancel it");
    }
}
