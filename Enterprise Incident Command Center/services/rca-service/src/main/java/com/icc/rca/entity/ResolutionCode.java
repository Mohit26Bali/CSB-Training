package com.icc.rca.entity;

/** How a problem ended. Set by the action that ends it, never typed (same four values as ServiceNow). */
public enum ResolutionCode {
    FIX_APPLIED,
    RISK_ACCEPTED,
    CANCELED,
    DUPLICATE
}
