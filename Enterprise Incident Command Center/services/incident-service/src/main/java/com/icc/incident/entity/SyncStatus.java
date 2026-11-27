package com.icc.incident.entity;

/**
 * Where a record is on its way to ServiceNow (DATA-MODEL, D9). Plain text constants, because the column is text.
 * LOCAL_ONLY: sync is switched off or the record was never changed since. PENDING: waiting for the next sync run
 * (also after a failure that is worth retrying). SYNCED: ServiceNow has the current version. FAILED: ServiceNow refused it
 * and retrying will not help until something is fixed (the reason is in last_sync_error).
 */
public final class SyncStatus {

    public static final String LOCAL_ONLY = "LOCAL_ONLY";
    public static final String PENDING = "PENDING";
    public static final String SYNCED = "SYNCED";
    public static final String FAILED = "FAILED";

    private SyncStatus() {
    }
}
