package com.hackathon.visitors.entity;

/** EXPECTED, then CHECKED_IN when security lets the visitor in, then CHECKED_OUT. An employee can CANCEL an invitation before arrival. */
public enum VisitStatus {
    EXPECTED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED
}
