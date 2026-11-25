package com.cartnest.exception;

/** The (simulated) payment gateway declined the payment. Nothing is saved. Answered with 402. */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
