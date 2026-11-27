package com.springlab.lab.trace;

/**
 * How trustworthy a step's claims are. This is the truthfulness contract of the learning lab:
 * <ul>
 *   <li>{@link #CAPTURED}: directly observed while the real request ran (the only provenance shown as LIVE).</li>
 *   <li>{@link #DERIVED}: inferred from captured facts plus SpringLab's own code; the inference is stated.</li>
 *   <li>{@link #CONCEPTUAL}: explains how the framework works; nothing about this step was observed.</li>
 * </ul>
 */
public enum Provenance {
    CAPTURED,
    DERIVED,
    CONCEPTUAL
}
