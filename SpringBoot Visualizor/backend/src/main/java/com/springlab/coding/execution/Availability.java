package com.springlab.coding.execution;

/** Whether learner code can be run right now, and if not, why. For internal use: it is never sent to a learner as-is. */
public record Availability(boolean available, String backend, String reason) {

    public static Availability up(String backend) {
        return new Availability(true, backend, "");
    }

    public static Availability down(String backend, String reason) {
        return new Availability(false, backend, reason);
    }
}
