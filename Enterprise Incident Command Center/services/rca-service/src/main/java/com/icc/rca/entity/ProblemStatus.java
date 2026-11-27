package com.icc.rca.entity;

/**
 * The six steps of a problem, in the order ServiceNow uses (DATA-MODEL, D31).
 * NEW, ASSESS, ROOT_CAUSE_ANALYSIS, FIX_IN_PROGRESS, RESOLVED, CLOSED.
 * A person does not set the status: each step is a named action (see ProblemAction).
 */
public enum ProblemStatus {
    NEW,
    ASSESS,
    ROOT_CAUSE_ANALYSIS,
    FIX_IN_PROGRESS,
    RESOLVED,
    CLOSED
}
