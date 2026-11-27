package com.springlab.coding.execution;

import org.springframework.http.HttpStatus;

/** No safe execution backend is available (503). The message is safe to show to a learner and holds no host detail. */
public class ExecutionUnavailableException extends ExecutionRejectedException {

    public ExecutionUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
