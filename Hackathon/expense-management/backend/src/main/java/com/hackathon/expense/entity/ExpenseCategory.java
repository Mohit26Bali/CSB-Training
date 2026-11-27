package com.hackathon.expense.entity;

public enum ExpenseCategory {
    TRAVEL("Travel"),
    MEALS("Meals"),
    ACCOMMODATION("Accommodation"),
    OFFICE_SUPPLIES("Office supplies"),
    SOFTWARE("Software"),
    TRAINING("Training"),
    OTHER("Other");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
