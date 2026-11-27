package com.springlab.lab.micro.core;

/** The implementation of one operation of one service. Nested calls to other services go through {@code context.bus()}. */
@FunctionalInterface
public interface ServiceHandler<Q, R> {

    ServiceResult<R> handle(CallContext context, Q request);
}
