package com.icc.servicenow;

/**
 * A call to ServiceNow failed. The kind says what to do about it, so the sync job can decide without reading text:
 * UNREACHABLE and SERVER_ERROR are worth another try later; the others will fail the same way until something is fixed.
 */
public class ServiceNowException extends RuntimeException {

    public enum Kind {
        /** The instance did not answer (asleep, network, timeout). */
        UNREACHABLE,
        /** 401: the integration user was refused (wrong password, locked, must reset its password). */
        UNAUTHORIZED,
        /** 403: the user may not do this (a role or an access rule is missing). */
        FORBIDDEN,
        /** 404: the record or the table does not exist. */
        NOT_FOUND,
        /** ServiceNow refused a value (for example a close code or a caller it does not know). */
        REJECTED_VALUE,
        /** Any other 4xx answer. */
        BAD_REQUEST,
        /** 5xx that is not a rejected value. */
        SERVER_ERROR
    }

    private final Kind kind;
    private final int status;

    public ServiceNowException(Kind kind, int status, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
    }

    public Kind kind() {
        return kind;
    }

    /** The HTTP status, or 0 when there was no answer. */
    public int status() {
        return status;
    }

    /** True when the same call may work later without any change (an instance that was asleep, a short outage). */
    public boolean isWorthRetrying() {
        return kind == Kind.UNREACHABLE || kind == Kind.SERVER_ERROR;
    }
}
