package com.springlab.lab.micro.core;

/**
 * What a caller can observe about one service call. These are deliberately different outcomes: they call for different reactions
 * (a retry is pointless after a business failure, dangerous after a timeout, and safe after UNAVAILABLE), and conflating them is
 * the commonest mistake in distributed code.
 */
public enum Outcome {
    /** The service did the work and said so. */
    SUCCESS,
    /** The service was reached, understood the request and refused it for a business reason (for example OUT_OF_STOCK). */
    BUSINESS_FAILURE,
    /** The service could not be reached at all; the request was never processed. */
    UNAVAILABLE,
    /** No answer arrived in time. The caller CANNOT know whether the work was done. */
    TIMEOUT,
    /** The service failed with an unexpected exception (a bug or a broken dependency), not a business decision. */
    TECHNICAL_ERROR,
    /** The call was refused locally because the target's concurrency permits (bulkhead) were all in use. Never reached the service. */
    BULKHEAD_REJECTED,
    /** The call was refused locally because the caller circuit breaker is OPEN. Never reached the service. */
    CIRCUIT_OPEN
}
