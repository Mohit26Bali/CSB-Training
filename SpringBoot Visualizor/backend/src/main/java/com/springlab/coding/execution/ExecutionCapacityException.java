package com.springlab.coding.execution;

import org.springframework.http.HttpStatus;

/** Every worker is busy and the queue is full (429). */
public class ExecutionCapacityException extends ExecutionRejectedException {

    public ExecutionCapacityException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "The code runner is busy right now. Try again in a moment");
    }
}
