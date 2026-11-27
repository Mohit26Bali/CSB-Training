package com.springlab.lab.micro.core;

/** What a service handler returns: a value, or a BUSINESS refusal with a code. Unexpected failures are exceptions, not results. */
public record ServiceResult<R>(boolean ok, R value, String code) {

    public static <R> ServiceResult<R> ok(R value) {
        return new ServiceResult<>(true, value, null);
    }

    public static <R> ServiceResult<R> failure(String code) {
        return new ServiceResult<>(false, null, code);
    }
}
