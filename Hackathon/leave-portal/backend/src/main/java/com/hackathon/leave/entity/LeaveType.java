package com.hackathon.leave.entity;

public enum LeaveType {
    ANNUAL("Annual leave", 18),
    SICK("Sick leave", 10),
    CASUAL("Casual leave", 6),
    /** Unpaid leave has no balance: it is not limited and not deducted from anything. */
    UNPAID("Unpaid leave", 0);

    private final String label;
    private final int yearlyAllowance;

    LeaveType(String label, int yearlyAllowance) {
        this.label = label;
        this.yearlyAllowance = yearlyAllowance;
    }

    public String getLabel() {
        return label;
    }

    public int getYearlyAllowance() {
        return yearlyAllowance;
    }

    /** Whether this type draws from a limited yearly balance. */
    public boolean isTracked() {
        return this != UNPAID;
    }
}
